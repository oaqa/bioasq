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

import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that counts the number (and ratio)
 * of the answer's occurrences in the question.
 * In contrast to {@link ConceptOverlappingCountAnswerScorer}, the {@Token} counts, which means
 * a synonym of the same question concept is used in the passage is ignored.
 *
 * @see ConceptOverlappingCountAnswerScorer
 * @see FocusOverlappingCountAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/30/15
 */
public class TokenOverlappingCountAnswerScorer extends AbstractScorer<Answer> {

  private Set<String> qtokens;

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    qtokens = TypeUtil.getOrderedTokens(jcas).stream()
            .flatMap(token -> Stream.of(token.getCoveredText(), token.getLemmaForm()))
            .map(String::toLowerCase).collect(toSet());
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    double[] qtokenOverlappingRatios = caos.stream()
            .map(cao -> JCasUtil.selectCovered(Token.class, cao)).mapToDouble(tokens -> Scorer
                    .safeDividedBy(countMatchingToken(tokens, qtokens), tokens.size())).toArray();
    return Scorer.generateSummaryFeatures(qtokenOverlappingRatios, "token-overlap", "avg",
            "pos-ratio", "any-one");
  }

  private static int countMatchingToken(Collection<Token> tokens, Set<String> qtokens) {
    return (int) tokens.stream().filter(token -> qtokens.contains(token.getLemmaForm()) ||
            qtokens.contains(token.getCoveredText().toLowerCase())).count();
  }

}
