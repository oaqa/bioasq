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

package edu.cmu.lti.oaqa.bioasq.eval.calculator;

import static edu.cmu.lti.oaqa.baseqa.eval.EvalCalculatorUtil.sumMeasurementValues;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.*;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import com.google.common.collect.Iterables;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.baseqa.eval.EvalCalculator;
import edu.cmu.lti.oaqa.baseqa.eval.EvalCalculatorUtil;
import edu.cmu.lti.oaqa.baseqa.eval.Measure;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * Implementations of BioASQ Phase B Factoid, List, and YesNo QA evaluation metrics.
 *
 * @see AnswerEvalMeasure
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class AnswerEvalCalculator<T extends Answer> extends ConfigurableProvider
        implements EvalCalculator<T> {

  @Override
  public Map<Measure, Double> calculate(JCas jcas, Collection<T> resultEvaluatees,
          Collection<T> gsEvaluatees, Comparator<T> comparator,
          Function<T, String> uniqueIdMapper) {
    Set<String> gsVariants = gsEvaluatees.stream().map(TypeUtil::getCandidateAnswerVariantNames)
            .flatMap(Collection::stream).map(String::toLowerCase).collect(toSet());
    List<Answer> resultAnswers = resultEvaluatees.stream().sorted(comparator).collect(toList());
    String questionType = TypeUtil.getQuestion(jcas).getQuestionType();
    ImmutableMap.Builder<Measure, Double> builder = ImmutableMap.builder();
    switch (questionType) {
      case "FACTOID":
        Set<String> strictResultVariants = resultAnswers.stream().limit(1)
                .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(Collection::stream)
                .map(String::toLowerCase).collect(toSet());
        Set<String> lenientResultVariants = resultAnswers.stream().limit(5)
                .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(Collection::stream)
                .map(String::toLowerCase).collect(toSet());
        int strictRetrieved = Sets.intersection(gsVariants, strictResultVariants).isEmpty() ? 0 : 1;
        builder.put(FACTOID_STRICT_RETRIEVED, (double) strictRetrieved);
        int lenientRetrieved = Sets.intersection(gsVariants, lenientResultVariants).isEmpty() ?
                0 :
                1;
        builder.put(FACTOID_LENIENT_RETRIEVED, (double) lenientRetrieved);
        double reciprocalRank = IntStream.range(0, resultAnswers.size())
                .filter(i -> TypeUtil.getCandidateAnswerVariantNames(resultAnswers.get(i)).stream()
                        .map(String::toLowerCase).anyMatch(gsVariants::contains))
                .mapToDouble(i -> 1.0 / (i + 1.0)).findFirst().orElse(0.0);
        builder.put(FACTOID_RECIPROCAL_RANK, reciprocalRank);
        builder.put(FACTOID_COUNT, 1.0);
        break;
      case "LIST":
        int relevantRetrieved = (int) resultAnswers.stream()
                .map(TypeUtil::getCandidateAnswerVariantNames).filter(names -> names.stream()
                        .map(String::toLowerCase).anyMatch(gsVariants::contains))
                .count();
        double precision = EvalCalculatorUtil.calculatePrecision(resultAnswers.size(),
                relevantRetrieved);
        builder.put(LIST_PRECISION, precision);
        double recall = EvalCalculatorUtil.calculateRecall(gsVariants.size(), relevantRetrieved);
        builder.put(LIST_RECALL, recall);
        builder.put(LIST_F1, EvalCalculatorUtil.calculateF1(precision, recall));
        builder.put(LIST_COUNT, 1.0);
        break;
      case "YES_NO":
        String gs = Iterables.getOnlyElement(gsVariants);
        String result = resultAnswers.stream().map(Answer::getText).findAny().orElse("");
        int correctRetrieved = gs.equals(result) ? 1 : 0;
        builder.put(YESNO_CORRECT, (double) correctRetrieved);
        if (gs.equals("yes")) {
          int truePositive = result.equals("yes") ? 1 : 0;
          builder.put(YESNO_TRUE_POS, (double) truePositive);
        } else {
          int trueNegative = result.equals("no") ? 1 : 0;
          builder.put(YESNO_TRUE_NEG, (double) trueNegative);
        }
        break;
    }
    return builder.build();
  }

  @Override
  public Map<Measure, Double> accumulate(
          Map<Measure, ? extends Collection<Double>> measure2values) {
    ImmutableMap.Builder<Measure, Double> builder = ImmutableMap.builder();
    if (measure2values.get(FACTOID_COUNT) != null) {
      double factoidCount = sumMeasurementValues(measure2values.get(FACTOID_COUNT));
      builder.put(FACTOID_COUNT, factoidCount);
      builder.put(FACTOID_STRICT_ACCURACY,
              sumMeasurementValues(measure2values.get(FACTOID_STRICT_RETRIEVED)) / factoidCount);
      builder.put(FACTOID_LENIENT_ACCURACY,
              sumMeasurementValues(measure2values.get(FACTOID_LENIENT_RETRIEVED)) / factoidCount);
      builder.put(FACTOID_MRR,
              sumMeasurementValues(measure2values.get(FACTOID_RECIPROCAL_RANK)) / factoidCount);
    }
    if (measure2values.get(LIST_COUNT) != null) {
      double listCount = sumMeasurementValues(measure2values.get(LIST_COUNT));
      builder.put(LIST_COUNT, listCount);
      builder.put(LIST_MEAN_PRECISION,
              sumMeasurementValues(measure2values.get(LIST_PRECISION)) / listCount);
      builder.put(LIST_MEAN_RECALL,
              sumMeasurementValues(measure2values.get(LIST_RECALL)) / listCount);
      builder.put(LIST_MEAN_F1, sumMeasurementValues(measure2values.get(LIST_F1)) / listCount);
    }
    if (measure2values.get(YESNO_CORRECT) != null) {
      Collection<Double> corrects = measure2values.get(YESNO_CORRECT);
      double yesnoCount = corrects.size();
      builder.put(YESNO_COUNT, yesnoCount);
      builder.put(YESNO_MEAN_ACCURACY, sumMeasurementValues(corrects) / yesnoCount);
      Collection<Double> truePositives = measure2values.get(YESNO_TRUE_POS);
      builder.put(YESNO_MEAN_POS_ACCURACY,
              sumMeasurementValues(truePositives) / truePositives.size());
      Collection<Double> trueNegatives = measure2values.get(YESNO_TRUE_NEG);
      builder.put(YESNO_MEAN_NEG_ACCURACY,
              sumMeasurementValues(trueNegatives) / trueNegatives.size());
    }
    return builder.build();
  }

  @Override
  public String getName() {
    return "Answer";
  }

}
