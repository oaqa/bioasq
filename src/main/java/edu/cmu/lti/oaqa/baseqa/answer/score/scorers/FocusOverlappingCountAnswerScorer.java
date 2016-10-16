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

package edu.cmu.lti.oaqa.baseqa.answer.score.scorers;

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that counts the number (and ratio)
 * of the question {@link Focus}'s occurrences in all the {@link CandidateAnswerOccurrence}s.
 * In comparison to {@link TokenOverlappingCountAnswerScorer}, only the {@link Focus} token is used,
 * instead of all the {@link Token}s in the question.
 *
 * @see TokenOverlappingCountAnswerScorer
 * @see ConceptOverlappingCountAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class FocusOverlappingCountAnswerScorer extends AbstractScorer<Answer> {

  private String focusLabel;

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    Focus focus = TypeUtil.getFocus(jcas);
    focusLabel = focus == null ? null : focus.getLabel();
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    if (focusLabel == null) return ImmutableMap.of();
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    double[] focusOverlappingRatios = caos.stream()
            .map(cao -> JCasUtil.selectCovered(Token.class, cao)).mapToDouble(tokens -> {
              long overlapCount = tokens.stream().map(Token::getLemmaForm)
                      .filter(focusLabel::equals).count();
              return Scorer.safeDividedBy(overlapCount, tokens.size());
            }).toArray();
    return Scorer
            .generateSummaryFeatures(focusOverlappingRatios, "focus-overlap", "avg", "pos-ratio");
  }

}
