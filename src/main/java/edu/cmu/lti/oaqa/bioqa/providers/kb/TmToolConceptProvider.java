/*
 * Open Advancement Question Answering (OAQA) Project Copyright 2016 Carnegie Mellon University
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations
 * under the License.
 */

package edu.cmu.lti.oaqa.bioqa.providers.kb;

import com.google.common.collect.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import org.apache.commons.io.IOUtils;
import org.apache.http.HttpResponse;
import org.apache.http.client.ServiceUnavailableRetryStrategy;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;
import org.apache.http.protocol.HttpContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.toList;

/**
 * <p>
 *   This {@link ConceptProvider} accesses
 *   <a href="https://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/PubTator/">NCBI PubTator</a> backend
 *   via <a href="https://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/tmTools/RESTfulAPIs.html">TmTool
 *   Restful API</a>, including <tt>tmChem</tt>, <tt>DNorm</tt>, <tt>tmVar</tt>, and
 *   <tt>GNormPlus</tt> services.
 * </p>
 * <p>
 *   We make a number of tweaks to make sure it works with tens of thousands of biomedical passages,
 *   including
 *   <ul>
 *     <li>
 *       The PubTator (tab-separated) format does not support <tt>DNorm</tt> annotation, and the
 *       BioC format (XML) can take forever to process a request.
 *       We use PubAnnotation (JSON) format.
 *       More information about the format can be found at
 *       <a href="http://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/tmTools/Format.html">http://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/tmTools/Format.html</a>.
 *     </li>
 *     <li>
 *       Offset might change, esp. with <tt>tmChem</tt> trigger. A few known issues/fixes:
 *       <ul>
 *         <li>
 *           The PubAnnotation RESTful service does not support some ASCII characters (e.g. '±').
 *           We create {@link PubAnnotationConvertUtil} to normalize the text before sending the
 *           request. Sometimes, the length may also change, e.g. in the case of "Waldispühl"
 *           (3 characters added).
 *         </li>
 *         <li>
 *           '%' is a dangerous symbol. We found that if a non whitespace character after it, the
 *           returned text will have a different length, e.g. '%-' or '%4', etc.
 *         </li>
 *         <li>Disabling HTML encoding while converting to JSON.</li>
 *       </ul>
 *     </li>
 *     <li>
 *       <tt>Receive/</tt> method can only be called once.
 *     </li>
 *   </ul>
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/19/16.
 */
public class TmToolConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private static final String URL_PREFIX = "https://www.ncbi.nlm.nih.gov/CBBresearch/Lu/Demo/RESTful/tmTool.cgi/";

  protected Set<String> triggers = ImmutableSet.of("tmChem", "DNorm", "tmVar", "GNormPlus");

  private static HttpClientBuilder clientBuilder = HttpClientBuilder.create()
          .disableRedirectHandling()
          .setServiceUnavailableRetryStrategy(new ServiceUnavailableRetryStrategy() {

            @Override
            public boolean retryRequest(HttpResponse response, int executionCount,
                    HttpContext context) {
              int statusCode = response.getStatusLine().getStatusCode();
              return statusCode == 404 || statusCode == 501;
            }

            @Override
            public long getRetryInterval() {
              return 1000L;
            }
          });

  private static Gson gson = new GsonBuilder().disableHtmlEscaping().create();

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    Object triggersParam = getParameterValue("triggers");
    if (triggersParam != null) {
      setTriggers(ImmutableSet.copyOf((Iterable<String>) triggersParam));
    }
    return ret;
  }

  public void setTriggers(Set<String> triggers) {
    this.triggers = triggers;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    return getConcepts(Collections.singletonList(jcas));
  }

  @Override
  public List<Concept> getConcepts(List<JCas> jcases) throws AnalysisEngineProcessException {
    // send request
    List<String> normalizedTexts = jcases.stream().map(JCas::getDocumentText)
            .map(PubAnnotationConvertUtil::normalizeText).collect(toList());
    ListMultimap<Integer, PubAnnotation.Denotation> index2denotations = Multimaps
            .synchronizedListMultimap(ArrayListMultimap.create());
    ExecutorService es = Executors.newCachedThreadPool();
    for (String trigger : triggers) {
      es.submit(() -> {
        try {
          List<String> denotationStrings = requestConcepts(normalizedTexts, trigger);
          assert denotationStrings.size() == jcases.size();
          for (int i = 0; i < jcases.size(); i++)  {
            PubAnnotation.Denotation[] denotations = gson
                    .fromJson(denotationStrings.get(i), PubAnnotation.Denotation[].class);
            index2denotations.putAll(i, Arrays.asList(denotations));
          }
        } catch (Exception e) {
          throw TmToolConceptProviderException.unknownException(trigger, e);
        }
      });
    }
    es.shutdown();
    try {
      boolean status = es.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);
      if (!status) {
        throw new AnalysisEngineProcessException();
      }
    } catch (InterruptedException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // convert denotation strings
    List<Concept> concepts = new ArrayList<>();
    for (int i = 0; i < jcases.size(); i++) {
      JCas jcas = jcases.get(i);
      List<PubAnnotation.Denotation> denotations = index2denotations.get(i);
      try {
        concepts.addAll(PubAnnotationConvertUtil.convertDenotationsToConcepts(jcas, denotations));
      } catch (StringIndexOutOfBoundsException e) {
        throw TmToolConceptProviderException
                .offsetOutOfBounds(jcas.getDocumentText(), denotations, e);
      }
    }
    return concepts;
  }

  protected List<String> requestConcepts(List<String> normalizedTexts, String trigger)
          throws AnalysisEngineProcessException {
    PubAnnotation[] inputs = PubAnnotationConvertUtil.convertTextsToPubAnnotations(normalizedTexts);
    String request = gson.toJson(inputs, PubAnnotation[].class);
    String response;
    try {
      response = submitText(trigger, request);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    PubAnnotation[] outputs = gson.fromJson("[" + response + "]", PubAnnotation[].class);
    List<PubAnnotation> sortedOutputs = Arrays.stream(outputs)
            .sorted(Comparator.comparing(pa -> Integer.parseInt(pa.getSourceid())))
            .collect(toList());
    List<String> denotationStrings = sortedOutputs.stream().map(PubAnnotation::getDenotations)
            .map(gson::toJson).collect(toList());
    if (denotationStrings.size() != normalizedTexts.size()) {
      throw TmToolConceptProviderException
              .unequalVolume(trigger, normalizedTexts.size(), denotationStrings.size());
    }
    for (int i = 0; i < normalizedTexts.size(); i++) {
      String sentText = normalizedTexts.get(i);
      String recvText = PubAnnotationConvertUtil.normalizeText(sortedOutputs.get(i).getText());
      if (sentText.length() != recvText.length()) {
        throw TmToolConceptProviderException.unequalTextLength(trigger, sentText, recvText);
      }
//      if (sentText.equals(recvText)) {
//        throw TmToolConceptProviderException.textChanged(trigger, sentText, recvText);
//      }
    }
    return denotationStrings;
  }

  private static String submitText(String trigger, String text) throws IOException {
    CloseableHttpClient client = clientBuilder.build();
    HttpPost post = new HttpPost(URL_PREFIX + trigger + "/Submit/");
    post.setEntity(new StringEntity(text));
    HttpResponse response = client.execute(post);
    String session = IOUtils.toString(response.getEntity().getContent());
    HttpGet get = new HttpGet(URL_PREFIX + session + "/Receive/");
    response = client.execute(get);
    return IOUtils.toString(response.getEntity().getContent());
  }

}
