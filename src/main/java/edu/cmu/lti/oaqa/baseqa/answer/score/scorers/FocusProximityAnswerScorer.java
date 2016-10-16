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
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that calculates the average distance
 * between the {@link Focus} token and each {@link CandidateAnswerOccurrence}.
 * In comparison to {@link TokenProximityAnswerScorer}, only the {@link Focus} token is used,
 * instead of all the {@link Token}s in the question.
 *
 * @see ConceptProximityAnswerScorer
 * @see TokenProximityAnswerScorer
 * @see ParseHeadProximityAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class FocusProximityAnswerScorer extends AbstractScorer<Answer> {

  private int windowSize;

  private double infinity;

  private double smoothing;

  private String focusLabel;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    windowSize = (int) getParameterValue("window-size");
    infinity = (double) getParameterValue("infinity");
    smoothing = (double) getParameterValue("smoothing");
    return ret;
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    Focus focus = TypeUtil.getFocus(jcas);
    focusLabel = focus == null ? null : focus.getLabel();
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    double[] precedingDistances = caos.stream().mapToDouble(cao -> {
      List<String> precedingTokens = JCasUtil.selectPreceding(Token.class, cao, windowSize)
              .stream().sorted(Comparator.comparing(Token::getEnd, Comparator.reverseOrder()))
              .map(Token::getLemmaForm).collect(toList());
      double precedingDistance = precedingTokens.indexOf(focusLabel);
      if (precedingDistance == -1)
        precedingDistance = infinity;
      return precedingDistance;
    }).toArray();
    double[] followingDistances = caos.stream().mapToDouble(cao -> {
      List<String> followingTokens = JCasUtil.selectFollowing(Token.class, cao, windowSize)
              .stream().sorted(Comparator.comparing(Token::getBegin)).map(Token::getLemmaForm)
              .collect(toList());
      double followingDistance = followingTokens.indexOf(focusLabel);
      if (followingDistance == -1)
        followingDistance = infinity;
      return followingDistance;
    }).toArray();
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    Scorer.generateSummaryDistanceFeatures(precedingDistances, builder, infinity, smoothing,
            "focus-preceding", "avg", "max", "min", "pos-ratio");
    Scorer.generateSummaryDistanceFeatures(followingDistances, builder, infinity, smoothing,
            "focus-following", "avg", "max", "min", "pos-ratio");
    return builder.build();
  }

}
