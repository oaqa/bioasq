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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that calculates the average distance
 * between each {@link CandidateAnswerOccurrence} and each mention of question token in the
 * relevant passages.
 * In contrast to {@link ConceptProximityAnswerScorer}, all the {@link Token}s counts, which means
 * a synonym of the same question concept is used in the passage is ignored.
 * Intuitively, the shorter the average distance, the more likely the answer is correct.
 *
 * @see ConceptProximityAnswerScorer
 * @see FocusProximityAnswerScorer
 * @see ParseHeadProximityAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/30/15
 */
public class TokenProximityAnswerScorer extends AbstractScorer<Answer> {

  private Set<String> stoplist;

  private int windowSize;

  private double infinity;

  private double smoothing;

  private Set<String> qtokens;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    windowSize = (int) getParameterValue("window-size");
    infinity = (double) getParameterValue("infinity");
    smoothing = (double) getParameterValue("smoothing");
    return ret;
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    qtokens = TypeUtil.getOrderedTokens(jcas).stream()
            .filter(token -> !stoplist.contains(token.getLemmaForm()) &&
                    !stoplist.contains(token.getCoveredText().toLowerCase()))
            .map(Token::getLemmaForm).collect(toSet());
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    // calculate the preceding distance between the CAO and each question token
    List<List<Double>> precedingDistances = caos.stream().map(cao -> {
      List<String> precedingTokens = JCasUtil.selectPreceding(Token.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(Token::getEnd, Comparator.reverseOrder()))
              .map(Token::getLemmaForm).collect(toList());
      return qtokens.stream().map(qtoken -> {
        double precedingDistance = precedingTokens.indexOf(qtoken);
        if (precedingDistance == -1)
          precedingDistance = infinity;
        return precedingDistance;
      } ).collect(toList());
    } ).collect(toList());
    // calculate the following distance between the CAO and each question token
    List<List<Double>> followingDistances = caos.stream().map(cao -> {
      List<String> followingTokens = JCasUtil.selectFollowing(Token.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(Token::getBegin)).map(Token::getLemmaForm)
              .collect(toList());
      return qtokens.stream().map(qtoken -> {
        double followingDistance = followingTokens.indexOf(qtoken);
        if (followingDistance == -1)
          followingDistance = infinity;
        return followingDistance;
      } ).collect(toList());
    } ).collect(toList());
    // create features
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    // average preceding distances
    double[] avgPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).average().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(avgPrecedingDistances, builder, infinity, smoothing,
            "token-avg-preceding", "avg", "max", "min", "pos-ratio");
    // min preceding distances
    double[] minPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).min().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(minPrecedingDistances, builder, infinity, smoothing,
            "token-min-preceding", "avg", "max", "min", "pos-ratio");
    // max preceding distances
    double[] maxPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).max().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(maxPrecedingDistances, builder, infinity, smoothing,
            "token-max-preceding", "avg", "max", "min", "pos-ratio");
    // average preceding distances
    double[] avgFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).average().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(avgFollowingDistances, builder, infinity, smoothing,
            "token-avg-following", "avg", "max", "min", "pos-ratio");
    // min preceding distances
    double[] minFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).min().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(minFollowingDistances, builder, infinity, smoothing,
            "token-min-following", "avg", "max", "min", "pos-ratio");
    // max preceding distances
    double[] maxFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).max().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(maxFollowingDistances, builder, infinity, smoothing,
            "token-max-following", "avg", "max", "min", "pos-ratio");
    return builder.build();
  }

}
