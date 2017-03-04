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

package edu.cmu.lti.oaqa.bioasq.concept.rerank.scorers;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Table;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.bio.bioasq.services.GoPubMedService;
import edu.cmu.lti.oaqa.bioasq.util.BioASQUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.PropertiesConfiguration;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link ConceptSearchResult}s that attempts to
 * retrieve the relevant {@link Concept}s using {@link GoPubMedService}.
 * Various different ways of constructing the queries, from only the tokens to a full combination of
 * tokens and concepts with all the synonyms contribute to the feature, and the retrieval scores are
 * used as the values.
 *
 * @see edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers.LuceneConceptScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class GoPubMedConceptRetrievalScorer extends AbstractScorer<ConceptSearchResult> {

  private GoPubMedService service;

  private int pages;

  private int hits;

  private int timeout;

  private Set<String> stoplist;

  private Table<String, String, Double> uri2conf2score;

  private Table<String, String, Integer> uri2conf2rank;

  private Set<String> confs;

  private static final Logger LOG = LoggerFactory.getLogger(GoPubMedConceptRetrievalScorer.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    String conf = String.class.cast(getParameterValue("conf"));
    PropertiesConfiguration gopubmedProperties = new PropertiesConfiguration();
    try {
      gopubmedProperties.load(getClass().getResourceAsStream(conf));
    } catch (ConfigurationException e) {
      throw new ResourceInitializationException(e);
    }
    service = new GoPubMedService(gopubmedProperties);
    pages = Integer.class.cast(getParameterValue("pages"));
    hits = Integer.class.cast(getParameterValue("hits"));
    timeout = Integer.class.cast(getParameterValue("timeout"));
    String stoplistPath = String.class.cast(getParameterValue("stoplist-path"));
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistPath), UTF_8).stream()
              .map(String::trim).collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    uri2conf2score = HashBasedTable.create();
    uri2conf2rank = HashBasedTable.create();
    return true;
  }

  private static String normalizeQuoteName(String name) {
    return "\"" + name.replaceAll("[^A-Za-z0-9_\\-]+", " ") + "\"";
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    List<String> tokens = TypeUtil.getOrderedTokens(jcas).stream().map(Token::getCoveredText)
            .map(name -> name.replaceAll("[^A-Za-z0-9_\\-]+", " ").trim())
            .filter(name -> !name.isEmpty() && !stoplist.contains(name.toLowerCase()))
            .collect(toList());
    List<String> wIdConceptNames = TypeUtil.getConcepts(jcas).stream()
            .filter(concept -> !TypeUtil.getConceptIds(concept).isEmpty())
            .map(TypeUtil::getConceptNames)
            .map(names -> names.stream().map(GoPubMedConceptRetrievalScorer::normalizeQuoteName)
                    .collect(joining(" ")))
            .collect(toList());
    List<String> woIdConceptNames = TypeUtil.getConcepts(jcas).stream()
            .filter(concept -> TypeUtil.getConceptIds(concept).isEmpty())
            .map(TypeUtil::getConceptNames)
            .map(names -> names.stream().map(GoPubMedConceptRetrievalScorer::normalizeQuoteName)
                    .collect(joining(" ")))
            .collect(toList());
    List<String> cmentionNames = TypeUtil.getConceptMentions(jcas).stream()
            .map(ConceptMention::getMatchedName)
            .map(GoPubMedConceptRetrievalScorer::normalizeQuoteName).collect(toList());
    ExecutorService es = Executors.newCachedThreadPool();
    // execute against all tokens
    String concatenatedTokens = String.join(" ", tokens);
    LOG.debug("Query string: {}", concatenatedTokens);
    for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
      es.execute(() -> {
        try {
          List<ConceptSearchResult> results = BioASQUtil
                  .searchOntology(service, jcas, concatenatedTokens, pages, hits, ontology);
          String conf = "tokens_concatenated@" + ontology.name();
          updateFeatureTable(results, conf);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } );
    }
    // execute against concatenated concept names
    String concatenatedConceptNames = String
            .join(" ", Iterables.concat(wIdConceptNames, woIdConceptNames));
    LOG.debug("Query string: {}", concatenatedConceptNames);
    for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
      es.execute(() -> {
        try {
          List<ConceptSearchResult> results = BioASQUtil
                  .searchOntology(service, jcas, concatenatedConceptNames, pages, hits, ontology);
          String conf = "concept_names_concatenated@" + ontology.name();
          updateFeatureTable(results, conf);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } );
    }
    // execute against concatenated concept mentions
    String concatenatedCmentions = String.join(" ", cmentionNames);
    LOG.debug("Query string: {}", concatenatedCmentions);
    for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
      es.execute(() -> {
        try {
          List<ConceptSearchResult> results = BioASQUtil
                  .searchOntology(service, jcas, concatenatedCmentions, pages, hits, ontology);
          String conf = "cmention_names_concatenated@" + ontology.name();
          updateFeatureTable(results, conf);
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
      } );
    }
    // execute against each concept name whose has an ID
    for (String conceptName : wIdConceptNames) {
      LOG.debug("Query string: {}", conceptName);
      for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
        es.execute(() -> {
          try {
            List<ConceptSearchResult> results = BioASQUtil
                    .searchOntology(service, jcas, conceptName, pages, hits, ontology);
            String conf = "w_id_concept_names_individual@" + ontology.name();
            updateFeatureTable(results, conf);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } );
      }
    }
    // execute against each concept name whose has no ID
    for (String conceptName : woIdConceptNames) {
      LOG.debug("Query string: {}", conceptName);
      for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
        es.execute(() -> {
          try {
            List<ConceptSearchResult> results = BioASQUtil
                    .searchOntology(service, jcas, conceptName, pages, hits, ontology);
            String conf = "wo_id_concept_names_individual@" + ontology.name();
            updateFeatureTable(results, conf);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } );
      }
    }
    // execute against each concept mention
    for (String cmentionName : cmentionNames) {
      LOG.debug("Query string: {}", cmentionName);
      for (BioASQUtil.Ontology ontology : BioASQUtil.Ontology.values()) {
        es.execute(() -> {
          try {
            List<ConceptSearchResult> results = BioASQUtil
                    .searchOntology(service, jcas, cmentionName, pages, hits, ontology);
            String conf = "cmention_names_individual@" + ontology.name();
            updateFeatureTable(results, conf);
          } catch (IOException e) {
            throw new RuntimeException(e);
          }
        } );
      }
    }
    es.shutdown();
    try {
      if (!es.awaitTermination(timeout, TimeUnit.MINUTES)) {
        LOG.warn("Timeout occurs for one or some concept retrieval services.");
      }
    } catch (InterruptedException e) {
      throw new AnalysisEngineProcessException(e);
    }
    confs = uri2conf2score.columnKeySet();
  }

  private void updateFeatureTable(List<ConceptSearchResult> results, String conf) {
    for (int i = 0; i < results.size(); i++) {
      ConceptSearchResult result = results.get(i);
      String uri = result.getUri();
      if (!uri2conf2rank.contains(uri, conf) || uri2conf2rank.get(uri, conf) > i) {
        uri2conf2rank.put(uri, conf, i);
      }
      double score = result.getScore();
      if (!uri2conf2score.contains(uri, conf) || uri2conf2score.get(uri, conf) < score) {
        uri2conf2score.put(uri, conf, score);
      }
    }
  }

  @Override
  public Map<String, Double> score(JCas jcas, ConceptSearchResult result) {
    ImmutableMap.Builder<String, Double> ret = new ImmutableMap.Builder<>();
    String uri = result.getUri();
    for (String conf : confs) {
      double rank = uri2conf2rank.contains(uri, conf) ?
              1.0 / (uri2conf2rank.get(uri, conf) + 1.0) :
              0.0;
      ret.put(conf + "/rank", rank);
      double score = uri2conf2score.contains(uri, conf) ?
              uri2conf2score.get(uri, conf) :
              0.0;
      ret.put(conf + "/score", score);
    }
    return ret.build();
  }

}
