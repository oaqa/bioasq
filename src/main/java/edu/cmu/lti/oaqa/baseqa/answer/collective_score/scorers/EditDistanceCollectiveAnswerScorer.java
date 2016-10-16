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
import java.util.stream.DoubleStream;

/**
 * A collective answer scorer that calculates the edit distance between two answer texts.
 * Intuitively, the answers to a list question tend to have similar suffixes or prefixes.
 *
 * @see ShapeDistanceCollectiveAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/15/15
 */
public class EditDistanceCollectiveAnswerScorer extends AbstractScorer<Answer> {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> distances;

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
    ImmutableSet<Answer> answerSet = ImmutableSet.copyOf(answers);
    SetMultimap<Answer, String> answer2names = HashMultimap.create();
    answers.forEach(answer -> TypeUtil.getCandidateAnswerVariantNames(answer).stream()
            .map(String::toLowerCase).forEach(name -> answer2names.put(answer, name)));
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer answer1 = pair.get(0);
      Answer answer2 = pair.get(1);
      if (answer1.equals(answer2)) {
        distances.put(answer1, answer2, 1.0);
      } else {
        Sets.cartesianProduct(answer2names.get(answer1), answer2names.get(answer2)).stream()
                .mapToDouble(namepair -> getDistance(namepair.get(0), namepair.get(1))).min()
                .ifPresent(x -> distances.put(answer1, answer2, 1.0 - x));
      }
    }
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Map<Answer, Double> neighbor2distance = distances.row(answer);
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    for (int topLimit : topLimits) {
      double[] distances = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2distance.getOrDefault(neighbor, 0.0)).toArray();
      builder.put("edit-distance-min-" + topLimit, Doubles.min(distances));
      builder.put("edit-distance-max-" + topLimit, Doubles.max(distances));
      builder.put("edit-distance-avg-" + topLimit,
              DoubleStream.of(distances).average().orElse(0.0));
    }
    return builder.build();
  }

  private double getDistance(String text1, String text2) {
    int distance = StringUtils.getLevenshteinDistance(text1, text2);
    return (double) distance / Math.max(text1.length(), text2.length());
  }

}
