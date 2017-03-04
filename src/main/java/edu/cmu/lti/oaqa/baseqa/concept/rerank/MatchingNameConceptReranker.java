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

package edu.cmu.lti.oaqa.baseqa.concept.rerank;

import com.google.common.base.CharMatcher;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * A {@link JCasAnnotator_ImplBase} that reranks the {@link ConceptSearchResult}s by the number of
 * concept names co-occurring in the retrieved candidate {@link ConceptSearchResult}s and the
 * original input question.
 * {@link edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers.MatchingNameConceptScorer} does a similar
 * job as a {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer}, but can be integrated into a
 * learning framework.
 *
 * @see edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers.MatchingNameConceptScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class MatchingNameConceptReranker extends JCasAnnotator_ImplBase {

  private static CharMatcher LD = CharMatcher.JAVA_LETTER_OR_DIGIT;

  private float bonus;

  private static final Logger LOG = LoggerFactory.getLogger(MatchingNameConceptReranker.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    bonus = UimaContextHelper.getConfigParameterFloatValue(context, "bonus", 100F);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Set<String> normalizedNames = TypeUtil.getConcepts(jcas).stream()
            .map(MatchingNameConceptReranker::getNormalizedNames).flatMap(Collection::stream)
            .collect(toSet());
    Collection<ConceptSearchResult> results = TypeUtil.getRankedConceptSearchResults(jcas);
    Set<ConceptSearchResult> bonusResults = results.stream()
            .filter(result -> getNormalizedNames(result.getConcept()).stream()
                    .anyMatch(normalizedNames::contains)).collect(toSet());
    bonusResults.forEach(result -> result.setScore(result.getScore() + bonus));
    if (LOG.isDebugEnabled()) {
      LOG.debug("Results that match names:");
      bonusResults.forEach(r -> LOG.debug(" - {}", TypeUtil.toString(r)));
    }
  }

  private static Set<String> getNormalizedNames(Concept concept) {
    return TypeUtil.getConceptNames(concept).stream().map(LD::retainFrom).map(String::toLowerCase)
            .collect(toSet());
  }

}
