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
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.DoubleStream;

/**
 * A collective answer scorer that calculates the distance between two answer text occurrences.
 * Intuitively, the answers to a list question tend to occur next to each other, e.g. the targeted
 * genes include Answer1, Answer2, and Answer3.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/15/15
 */
public class DistanceCollectiveAnswerScorer extends AbstractScorer<Answer> {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> distances;

  private Table<Answer, Answer, Double> ndistances;

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
    SetMultimap<Answer, CandidateAnswerOccurrence> answer2caos = HashMultimap.create();
    answers.forEach(answer -> TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences)
            .forEach(caos -> answer2caos.putAll(answer, caos)));
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer answer1 = pair.get(0);
      Answer answer2 = pair.get(1);
      if (answer1.equals(answer2)) {
        distances.put(answer1, answer2, 1.0);
      } else {
        Sets.cartesianProduct(answer2caos.get(answer1), answer2caos.get(answer2)).stream()
                .filter(DistanceCollectiveAnswerScorer::allInTheSameView)
                .mapToInt(caopair -> getDistance(caopair.get(0), caopair.get(1))).min()
                .ifPresent(x -> distances.put(answer1, answer2, 1.0 / (1.0 + x)));
      }
    }
    ndistances = normalize(distances);
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Map<Answer, Double> neighbor2distance = distances.row(answer);
    Map<Answer, Double> neighbor2ndistance = ndistances.row(answer);
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    for (int topLimit : topLimits) {
      double[] distances = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2distance.getOrDefault(neighbor, 0.0)).toArray();
      builder.put("distance-min-" + topLimit, Doubles.min(distances));
      builder.put("distance-max-" + topLimit, Doubles.max(distances));
      builder.put("distance-avg-" + topLimit, DoubleStream.of(distances).average().orElse(0.0));
      double[] ndistances = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2ndistance.getOrDefault(neighbor, 0.0)).toArray();
      builder.put("ndistance-min-" + topLimit, Doubles.min(ndistances));
      builder.put("ndistance-max-" + topLimit, Doubles.max(ndistances));
      builder.put("ndistance-avg-" + topLimit, DoubleStream.of(ndistances).average().orElse(0.0));
    }
    return builder.build();
  }

  private static <K1, K2> Table<K1, K2, Double> normalize(Table<K1, K2, Double> orig) {
    Table<K1, K2, Double> ret = HashBasedTable.create();
    orig.rowMap().entrySet().forEach(entry -> {
      K1 key1 = entry.getKey();
      double sum = entry.getValue().values().stream().mapToDouble(x -> x).sum();
      entry.getValue().entrySet().forEach(e -> ret.put(key1, e.getKey(), e.getValue() / sum));
    });
    return ret;
  }

  private static boolean allInTheSameView(Collection<? extends TOP> tops) {
    return tops.stream().map(TOP::getCAS).distinct().count() == 1;
  }

  private static int getDistance(AnnotationFS annotation1, AnnotationFS annotation2) {
    if (annotation1.getEnd() < annotation2.getBegin()) {
      return JCasUtil.selectBetween(Token.class, annotation1, annotation2).size();
    } else if (annotation1.getBegin() > annotation2.getEnd()) {
      return JCasUtil.selectBetween(Token.class, annotation2, annotation1).size();
    } else {
      return 0;
    }
  }

}
