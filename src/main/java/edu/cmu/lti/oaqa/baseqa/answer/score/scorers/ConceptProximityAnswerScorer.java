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
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
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
 * between each {@link CandidateAnswerOccurrence} and each mention of each key concept (i.e.
 * question concepts) in the relevant passages.
 * In contrast to {@link TokenProximityAnswerScorer}, only the {@link ConceptMention} counts,
 * which means (1) a synonym of the same question concept is used in the passage is admitted, (2) an
 * overlapping phrase or word that is not a {@link Concept} is ignored.
 * Intuitively, the shorter the average distance, the more likely the answer is correct.
 *
 * @see TokenProximityAnswerScorer
 * @see FocusProximityAnswerScorer
 * @see ParseHeadProximityAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/30/15
 */
public class ConceptProximityAnswerScorer extends AbstractScorer<Answer> {

  private Set<String> stoplist;

  private int windowSize;

  private double infinity;

  private double smoothing;

  private Set<Concept> qconcepts;

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
    // collection concepts in the question
    qconcepts = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .filter(cmention -> !stoplist.contains(cmention.getCoveredText().toLowerCase()))
            .map(ConceptMention::getConcept).collect(toSet());
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    // calculate the preceding distance between each pair of CAO and question concept.
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer)
            .stream().map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    List<List<Double>> precedingDistances = caos.stream().map(cao -> {
      List<Concept> precedingConcepts = JCasUtil
              .selectPreceding(ConceptMention.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(ConceptMention::getEnd, Comparator.reverseOrder()))
              .map(ConceptMention::getConcept).collect(toList());
      return qconcepts.stream().map(qconcept -> {
        double precedingDistance = precedingConcepts.indexOf(qconcept);
        if (precedingDistance == -1)
          precedingDistance = infinity;
        return precedingDistance;
      }).collect(toList());
    }).collect(toList());
    // calculate the following distance between each pair of CAO and question concept.
    List<List<Double>> followingDistances = caos.stream().map(cao -> {
      List<Concept> followingConcepts = JCasUtil
              .selectFollowing(ConceptMention.class, cao, windowSize).stream()
              .sorted(Comparator.comparing(ConceptMention::getBegin))
              .map(ConceptMention::getConcept).collect(toList());
      return qconcepts.stream().map(qconcept -> {
        double followingDistance = followingConcepts.indexOf(qconcept);
        if (followingDistance == -1)
          followingDistance = infinity;
        return followingDistance;
      }).collect(toList());
    }).collect(toList());
    // create features
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    // average preceding distances
    double[] avgPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).average().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(avgPrecedingDistances, builder, infinity, smoothing,
            "concept-avg-preceding", "avg", "max", "min", "pos-ratio");
    // min preceding distances
    double[] minPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).min().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(minPrecedingDistances, builder, infinity, smoothing,
            "concept-min-preceding", "avg", "max", "min", "pos-ratio");
    // max preceding distances
    double[] maxPrecedingDistances = precedingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).max().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(maxPrecedingDistances, builder, infinity, smoothing,
            "concept-max-preceding", "avg", "max", "min", "pos-ratio");
    // average preceding distances
    double[] avgFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).average().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(avgFollowingDistances, builder, infinity, smoothing,
            "concept-avg-following", "avg", "max", "min", "pos-ratio");
    // min preceding distances
    double[] minFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).min().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(minFollowingDistances, builder, infinity, smoothing,
            "concept-min-following", "avg", "max", "min", "pos-ratio");
    // max preceding distances
    double[] maxFollowingDistances = followingDistances.stream()
            .mapToDouble(list -> list.stream().mapToDouble(x -> x).max().orElse(infinity))
            .toArray();
    Scorer.generateSummaryDistanceFeatures(maxFollowingDistances, builder, infinity, smoothing,
            "concept-max-following", "avg", "max", "min", "pos-ratio");
    return builder.build();
  }

}
