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

package edu.cmu.lti.oaqa.bioasq.document.retrieval;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.bio.bioasq.services.GoPubMedService;
import edu.cmu.lti.oaqa.bioasq.util.BioASQUtil;
import edu.cmu.lti.oaqa.bioqa.providers.query.PubMedQueryStringConstructor;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A {@link JCasAnnotator_ImplBase} that performs search using a query string, transformed from a
 * {@link AbstractQuery} by a {@link QueryStringConstructor} to retrieve relevant
 * {@link Document}s from {@link GoPubMedService}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.document.retrieval.LuceneDocumentRetrievalExecutor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/3/14
 */
public class GoPubMedDocumentRetrievalExecutor extends JCasAnnotator_ImplBase {

  private GoPubMedService service;

  private int pages;

  private int hits;

  private QueryStringConstructor queryStringConstructor;

  private static final Logger LOG = LoggerFactory
          .getLogger(GoPubMedDocumentRetrievalExecutor.class);

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
    queryStringConstructor = new PubMedQueryStringConstructor();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<AbstractQuery> aqueries = TypeUtil.getAbstractQueries(jcas);
    List<Document> documents = new ArrayList<>();
    Set<String> docIds = new HashSet<>();
    for (AbstractQuery aquery : aqueries) {
      try {
        String queryString = queryStringConstructor.construct(aquery);
        LOG.info("Search for query: {}", queryString);
        BioASQUtil.searchPubMed(service, jcas, queryString, pages, hits).stream()
                .filter(doc -> !docIds.contains(doc.getDocId())).forEach(doc -> {
          documents.add(doc);
          docIds.add(doc.getDocId());
        });
        LOG.info("Retrieved: {}", documents.size());
        if (documents.size() > 10) {
          break;
        }
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    documents.forEach(Document::addToIndexes);
  }
}
