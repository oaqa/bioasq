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

package edu.cmu.lti.oaqa.baseqa.answer.collective_score.scorers;

import com.google.common.collect.*;
import com.google.common.primitives.Doubles;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.commons.lang.StringUtils;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.stream.DoubleStream;

/**
 * A collective answer scorer that calculates the edit distance between each pair of answer texts,
 * after the original texts are transformed into their shape forms, by placing [A-Z] with "A", [a-z]
 * with "a", [0-9] with "0", and [^A-Za-z0-9] with "_".
 *
 * @see EditDistanceCollectiveAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/15/15
 */
public class ShapeDistanceCollectiveAnswerScorer extends AbstractScorer<Answer> {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> distances;

  private Table<Answer, Answer, Double> bdistances;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    topLimits = (Iterable<Integer>) getParameterValue("top-limit");
    return ret;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void prepare(JCas jcas) {
    answers = TypeUtil.getRankedAnswers(jcas);
    distances = HashBasedTable.create();
    bdistances = HashBasedTable.create();
    ImmutableSet<Answer> answerSet = ImmutableSet.copyOf(answers);
    SetMultimap<Answer, String> answer2shapes = HashMultimap.create();
    answers.forEach(answer -> TypeUtil.getCandidateAnswerVariantNames(answer).stream()
            .map(ShapeDistanceCollectiveAnswerScorer::shape)
            .forEach(shape -> answer2shapes.put(answer, shape)));
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer answer1 = pair.get(0);
      Answer answer2 = pair.get(1);
      if (answer1.equals(answer2)) {
        distances.put(answer1, answer2, 1.0);
        bdistances.put(answer1, answer2, 1.0);
      } else {
        OptionalDouble distance = Sets
                .cartesianProduct(answer2shapes.get(answer1), answer2shapes.get(answer2)).stream()
                .mapToDouble(shapepair -> getDistance(shapepair.get(0), shapepair.get(1))).min();
        if (distance.isPresent()) {
          distances.put(answer1, answer2, 1.0 - distance.getAsDouble());
          bdistances.put(answer1, answer2, distance.getAsDouble() == 0.0 ? 1.0 : 0.0);
        }
      }
    }
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    Map<Answer, Double> neighbor2distance = distances.row(answer);
    Map<Answer, Double> neighbor2bdistance = bdistances.row(answer);
    for (int topLimit : topLimits) {
      double[] distances = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2distance.getOrDefault(neighbor, 0.0)).toArray();
      builder.put("shape-distance-min-" + topLimit, Doubles.min(distances));
      builder.put("shape-distance-max-" + topLimit, Doubles.max(distances));
      builder.put("shape-distance-avg-" + topLimit,
              DoubleStream.of(distances).average().orElse(0.0));
      double[] bdistances = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2bdistance.getOrDefault(neighbor, 0.0)).toArray();
      builder.put("shape-bdistance-min-" + topLimit, Doubles.min(bdistances));
      builder.put("shape-bdistance-max-" + topLimit, Doubles.max(bdistances));
      builder.put("shape-bdistance-avg-" + topLimit,
              DoubleStream.of(bdistances).average().orElse(0.0));
    }
    return builder.build();
  }

  private double getDistance(String text1, String text2) {
    int distance = StringUtils.getLevenshteinDistance(text1, text2);
    return (double) distance / Math.max(text1.length(), text2.length());
  }

  private static String shape(String text) {
    return text.replaceAll("[A-Z]", "A").replaceAll("[a-z]", "a").replaceAll("[0-9]", "0")
            .replaceAll("[^A-Za-z0-9]", "_");
  }

}
