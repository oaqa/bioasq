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

package edu.cmu.lti.oaqa.baseqa.learning_base;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;
import java.util.stream.DoubleStream;

/**
 * An implementation of this interface can evidence an instance in the input {@link JCas} by
 * providing one or many feature values. The implementations should be integrated into
 * {@link ClassifierTrainer} and {@link ClassifierPredictor} for training and prediction.
 *
 * @see ClassifierTrainer
 * @see ClassifierPredictor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/9/16
 */
public interface Scorer<T> extends Resource {

  default void prepare(JCas jcas) throws AnalysisEngineProcessException {}

  Map<String, Double> score(JCas jcas, T candidate);

  static void generateSummaryDistanceFeatures(double[] distances,
          ImmutableMap.Builder<String, Double> builder, double infinity, double smoothing,
          String keyword, String... operators) {
//    double[] avgPrecedingNegdistances = Arrays.stream(distances)
//            .map(distance -> distance - infinity).toArray();
//    builder.putAll(generateSummaryFeatures(avgPrecedingNegdistances, keyword + "-negdistance",
//                    operators));
    double[] avgPrecedingProximities = Arrays.stream(distances)
            .map(distance -> 1.0 / (smoothing + distance)).toArray();
    builder.putAll(
            generateSummaryFeatures(avgPrecedingProximities, keyword + "-proximity", operators));
  }

  static Map<String, Double> generateSummaryFeatures(double[] ratios, String keyword) {
    return generateSummaryFeatures(ratios, keyword, "avg", "max", "min", "pos-ratio", "one-ratio",
            "any-one");
  }

  static Map<String, Double> generateSummaryFeatures(double[] ratios, String keyword,
          String... operators) {
    ImmutableMap.Builder<String, Double> feat2value = ImmutableMap.builder();
    Set<String> operatorSet = ImmutableSet.copyOf(operators);
    if (operatorSet.contains("avg")) {
      feat2value.put(keyword + "-avg", DoubleStream.of(ratios).average().orElse(0));
    }
    if (operatorSet.contains("max")) {
      feat2value.put(keyword + "-max", DoubleStream.of(ratios).max().orElse(0));
    }
    if (operatorSet.contains("min")) {
      feat2value.put(keyword + "-min", DoubleStream.of(ratios).min().orElse(0));
    }
    if (operatorSet.contains("pos-ratio")) {
      feat2value.put(keyword + "-pos-ratio",
              DoubleStream.of(ratios).mapToInt(r -> r == 0.0 ? 0 : 1).average().orElse(0));
    }
    if (operatorSet.contains("one-ratio")) {
      feat2value.put(keyword + "-one-ratio",
              DoubleStream.of(ratios).mapToInt(r -> r == 1.0 ? 1 : 0).average().orElse(0));
    }
    if (operatorSet.contains("any-one")) {
      feat2value.put(keyword + "-any-one",
              DoubleStream.of(ratios).anyMatch(r -> r == 1.0) ? 1.0 : 0.0);
    }
    return feat2value.build();
  }

  static double safeDividedBy(double x, double y) {
    if (x == 0.0 && y == 0.0) {
      return 0.0;
    } else {
      return x / y;
    }
  }

}