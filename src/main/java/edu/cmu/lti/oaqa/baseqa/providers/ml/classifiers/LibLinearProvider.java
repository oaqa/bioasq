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

package edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import de.bwaldvogel.liblinear.*;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * <p>
 *   A {@link ClassifierProvider} that wraps
 *   <a href="https://www.csie.ntu.edu.tw/~cjlin/liblinear/">LibLinear</a> classifiers.
 *   A descriptor of this {@link ConfigurableProvider} should specify the <tt>solver-type</tt>.
 * </p>
 * <p>
 *   Other parameters include <tt>model-file</tt>, <tt>feat-index-file</tt>,
 *   <tt>label-index-file</tt>, etc.
 *   The latter two map between the string-based feature and label names to integers (indexes).
 * </p>
 * <p>
 *   Note that it has a special
 *   <a href="https://www.csie.ntu.edu.tw/~cjlin/liblinear/COPYRIGHT">copyright</a>.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/15
 */
public class LibLinearProvider extends ConfigurableProvider implements ClassifierProvider {

  private File featIndexFile;

  private File labelIndexFile;

  private File modelFile;

  private boolean balanceWeight;

  private Map<Integer, String> fid2feat;

  private BiMap<Integer, String> lid2label;

  private BiMap<String, Integer> label2lid;

  private Model model;

  private Parameter parameter;

  private static final Logger LOG = LoggerFactory.getLogger(LibLinearProvider.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // feature id map
    if ((featIndexFile = new File((String) getParameterValue("feat-index-file"))).exists()) {
      try {
        fid2feat = ClassifierProvider.loadIdKeyMap(featIndexFile);
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // label id map
    if ((labelIndexFile = new File((String) getParameterValue("label-index-file"))).exists()) {
      try {
        lid2label = HashBiMap.create(ClassifierProvider.loadIdKeyMap(labelIndexFile));
        label2lid = lid2label.inverse();
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // model
    if ((modelFile = new File((String) getParameterValue("model-file"))).exists()) {
      try {
        model = Model.load(modelFile);
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    balanceWeight = (boolean) getParameterValue("balance-weight");
    // parameter
    SolverType solver = SolverType.valueOf((String) getParameterValue("solver-type")); // -s 0
    double C = 1.0; // cost of constraints violation
    double eps = 0.01; // stopping criteria
    parameter = new Parameter(solver, C, eps);
    Linear.disableDebugOutput();
    return ret;
  }

  @Override
  public Map<String, Double> infer(Map<String, Double> features) {
    Feature[] x = IntStream.range(1, fid2feat.size() + 1)
            .mapToObj(j -> new FeatureNode(j, features.getOrDefault(fid2feat.get(j), 0.0)))
            .toArray(Feature[]::new);
    double[] values = new double[lid2label.size()];
    Linear.predictValues(model, x, values);
    if (lid2label.size() == 2) {
      values[0] = 1 / (1 + Math.exp(-values[0]));
      values[1] = 1 - values[0];
    }
    int[] lids = model.getLabels();
    return IntStream.range(0, values.length).boxed()
            .collect(toMap(i -> lid2label.get(lids[i]), i -> values[i]));
  }

  @Override
  public List<String> predict(Map<String, Double> features, int k)
          throws AnalysisEngineProcessException {
    Feature[] x = IntStream.range(1, fid2feat.size() + 1)
            .mapToObj(j -> new FeatureNode(j, features.getOrDefault(fid2feat.get(j), 0.0)))
            .toArray(Feature[]::new);
    double[] values = new double[lid2label.size()];
    double result = Linear.predictProbability(model, x, values);
    int[] lids = model.getLabels();
    Map<String, Double> label2value = IntStream.range(0, lid2label.size()).boxed()
            .collect(toMap(j -> lid2label.get(lids[j]), j -> values[j]));
    List<String> topK = label2value.entrySet().stream()
            .sorted(Map.Entry.<String, Double>comparingByValue().reversed()).limit(k)
            .map(Map.Entry::getKey).collect(toList());
    assert lid2label.get((int) result).equals(topK.get(0));
    return topK;
  }

  @Override
  public void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException {
    // create feature to id map
    fid2feat = ClassifierProvider.createFeatureIdKeyMap(X);
    // create label to id map
    lid2label = ClassifierProvider.createLabelIdKeyMap(Y);
    label2lid = lid2label.inverse();
    try {
      ClassifierProvider.saveIdKeyMap(fid2feat, featIndexFile);
      ClassifierProvider.saveIdKeyMap(lid2label, labelIndexFile);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // train model
    Problem prob = new Problem();
    assert X.size() == Y.size();
    int dataCount = X.size();
    int featCount = fid2feat.size();
    LOG.info("Training for {} instances, {} features, {} labels.", dataCount, featCount,
            lid2label.size());
    prob.l = dataCount;
    prob.n = featCount;
    prob.x = X.stream()
            .map(x -> IntStream.range(1, featCount + 1)
                    .mapToObj(j -> new FeatureNode(j, x.getOrDefault(fid2feat.get(j), 0.0)))
                    .toArray(Feature[]::new))
            .toArray(Feature[][]::new);
    prob.y = Y.stream().mapToDouble(label2lid::get).toArray();
    if (balanceWeight) {
      Map<String, Long> y2count = Y.stream().collect(groupingBy(Function.identity(), counting()));
      double yMax = Collections.max(y2count.values());
      Map<String, Double> y2weight = y2count.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, entry -> yMax / entry.getValue()));
      double[] weights = y2weight.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray();
      int[] weightLabels = y2weight.entrySet().stream().map(Map.Entry::getKey)
              .mapToInt(label2lid::get).toArray();
      parameter.setWeights(weights, weightLabels);
    }
    model = Linear.train(prob, parameter);
    try {
      model.save(modelFile);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    if (crossValidation) {
      crossValidate(prob, 10);
    }
  }

  public void crossValidate(Problem problem, int nrFold) {
    double[] target = new double[problem.l];
    Linear.crossValidation(problem, parameter, nrFold, target);
    long totalCorrect = IntStream.range(0, problem.l).filter(i -> target[i] == problem.y[i])
            .count();
    System.out.printf("correct: %d%n", totalCorrect);
    System.out.printf("Cross Validation Accuracy = %g%%%n", 100.0 * totalCorrect / problem.l);
  }

}
