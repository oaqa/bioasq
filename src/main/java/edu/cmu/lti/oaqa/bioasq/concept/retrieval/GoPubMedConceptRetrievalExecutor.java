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

package edu.cmu.lti.oaqa.bioasq.concept.retrieval;

import edu.cmu.lti.oaqa.baseqa.providers.query.BagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.bio.bioasq.services.GoPubMedService;
import edu.cmu.lti.oaqa.bioasq.util.BioASQUtil;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.util.stream.Collectors.groupingBy;

/**
 * A {@link JCasAnnotator_ImplBase} that performs search using a query string, transformed from a
 * {@link AbstractQuery} by a {@link QueryStringConstructor} to retrieve relevant
 * {@link ConceptSearchResult}s from {@link GoPubMedService}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.concept.retrieval.LuceneConceptRetrievalExecutor
 * @see GoPubMedSeparateConceptRetrievalExecutor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/3/14
 */
public class GoPubMedConceptRetrievalExecutor extends JCasAnnotator_ImplBase {

  private GoPubMedService service;

  private int pages;

  private int hits;

  private QueryStringConstructor bopQueryStringConstructor;

  private long timeout;

  private int limit;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conf = UimaContextHelper.getConfigParameterStringValue(context, "conf");
    PropertiesConfiguration gopubmedProperties = new PropertiesConfiguration();
    try {
      gopubmedProperties.load(getClass().getResourceAsStream(conf));
    } catch (ConfigurationException e) {
      throw new ResourceInitializationException(e);
    }
    service = new GoPubMedService(gopubmedProperties);
    pages = UimaContextHelper.getConfigParameterIntValue(context, "pages", 1);
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 100);
    bopQueryStringConstructor = new BagOfPhraseQueryStringConstructor();
    timeout = UimaContextHelper.getConfigParameterIntValue(context, "timeout", 4);
    limit = UimaContextHelper.getConfigParameterIntValue(context, "limit", Integer.MAX_VALUE);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).stream().findFirst().get();
    String queryString = bopQueryStringConstructor.construct(aquery)
            .replaceAll("[^A-Za-z0-9_\\-\"]+", " ");
    System.out.println("Query String: " + queryString);
    List<ConceptSearchResult> concepts = Collections.synchronizedList(new ArrayList<>());
    ExecutorService es = Executors.newCachedThreadPool();
    for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
      es.execute(() -> {
        try {
          concepts.addAll(
                  BioASQUtil.searchOntology(service, jcas, queryString, pages, hits, ontology));
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } );
    }
    es.shutdown();
    try {
      if (!es.awaitTermination(timeout, TimeUnit.MINUTES)) {
        System.out.println("Timeout occurs for one or some concept retrieval service.");
      }
    } catch (InterruptedException e) {
      throw new AnalysisEngineProcessException(e);
    }
    Map<String, List<ConceptSearchResult>> onto2concepts = concepts.stream()
            .collect(groupingBy(ConceptSearchResult::getSearchId));
    for (Map.Entry<String, List<ConceptSearchResult>> entry : onto2concepts.entrySet()) {
      List<ConceptSearchResult> results = entry.getValue();
      System.out.println("Retrieved " + results.size() + " concepts from " + entry.getKey());
      results.stream().limit(3).forEach(c -> System.out.println(" - " + TypeUtil.toString(c)));
    }
    TypeUtil.rankedSearchResultsByScore(concepts, limit).forEach(ConceptSearchResult::addToIndexes);
  }

}
