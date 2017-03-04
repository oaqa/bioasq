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

package edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers;

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * <p>
 *   This implementation is a special case of {@link ConceptOverlapYesNoScorer}, by focusing only
 *   on the expected answer (here we use the last {@link ConceptMention} in the question), instead
 *   all the concepts and tokens.
 * </p>
 * <p>
 *   It counts the frequency (and the percentage) that the expected answer is mentioned in the
 *   relevant passages, as well as the frequency that {@link Concept}s of the same type are
 *   mentioned.
 * </p>
 *
 * @see ConceptOverlapYesNoScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class ExpectedAnswerOverlapYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  private String viewNamePrefix;

  private static final Logger LOG = LoggerFactory.getLogger(ExpectedAnswerOverlapYesNoScorer.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    viewNamePrefix = String.class.cast(getParameterValue("view-name-prefix"));
    return true;
  }

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    // assume the last concept mention in the question as the expected answer
    ConceptMention lastCmention = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .min(Comparator.comparingInt(ConceptMention::getEnd).reversed()
                    .thenComparingInt(ConceptMention::getBegin)).orElse(null);
    if (lastCmention == null) return ImmutableMap.of();
    // find all concepts that correspond to the offsets of the last cmention
    int begin = lastCmention.getBegin();
    int end = lastCmention.getEnd();
    Set<Concept> lastConcepts = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .filter(cmention -> cmention.getBegin() == begin && cmention.getEnd() == end)
            .map(ConceptMention::getConcept).collect(toSet());
    Set<String> expectedAnswerNames = lastConcepts.stream().map(TypeUtil::getConceptNames).flatMap(
            Collection::stream).map(String::toLowerCase).collect(toSet());
    LOG.info("Expected answer names: {}", expectedAnswerNames);
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Integer> containsExpectedAnswerNames = new ArrayList<>();
    List<Integer> containsLastConcepts = new ArrayList<>();
    for (JCas view : views) {
      String text = view.getDocumentText().toLowerCase();
      boolean containsExpectedAnswerName = expectedAnswerNames.stream().anyMatch(text::contains);
      containsExpectedAnswerNames.add(containsExpectedAnswerName ? 1 : 0);
      boolean containsLastConcept = TypeUtil.getConceptMentions(view).stream()
              .map(ConceptMention::getConcept).anyMatch(lastConcepts::contains);
      containsLastConcepts.add(containsLastConcept ? 1 : 0);
    }
    ImmutableMap.Builder<String, Double> features = ImmutableMap.builder();
    features.putAll(
            YesNoScorer.aggregateFeatures(containsExpectedAnswerNames, "expected-answer-overlap"));
    features.putAll(YesNoScorer.aggregateFeatures(containsLastConcepts, "last-concept-overlap"));
    return features.build();
  }

}
