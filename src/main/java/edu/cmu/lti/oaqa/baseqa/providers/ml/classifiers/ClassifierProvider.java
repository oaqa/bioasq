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

import com.google.common.base.Charsets;
import com.google.common.collect.*;
import com.google.common.io.Files;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

/**
 * The interface for wrapping a classifier.
 * It is used by both general-purpose
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer} and
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor} classes, and specific
 * classifier training and prediction classes, e.g.
 * {@link edu.cmu.lti.oaqa.baseqa.answer_type.AnswerTypeClassifierTrainer} and
 * {@link edu.cmu.lti.oaqa.baseqa.answer_type.AnswerTypeClassifierPredictor}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/15
 */
public interface ClassifierProvider extends Resource {

  enum ResampleType {DOWN, UP, NONE}

  Logger LOG = LoggerFactory.getLogger(ClassifierProvider.class);

  Map<String, Double> infer(Map<String, Double> features) throws AnalysisEngineProcessException;

  default double infer(Map<String, Double> features, String label)
          throws AnalysisEngineProcessException {
    return infer(features).get(label);
  }

  default String predict(Map<String, Double> features) throws AnalysisEngineProcessException {
    return infer(features).entrySet().stream().max(Comparator.comparing(Map.Entry::getValue))
            .orElseThrow(AnalysisEngineProcessException::new).getKey();
  }

  default List<String> predict(Map<String, Double> features, int k)
          throws AnalysisEngineProcessException {
    return infer(features).entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry::getValue, Comparator.reverseOrder()))
            .map(Map.Entry::getKey).limit(k).collect(toList());
  }

  void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException;

  default void trainMultiLabel(List<Map<String, Double>> X, List<Collection<String>> Y,
          ResampleType resampleType, boolean crossValidation) throws AnalysisEngineProcessException {
    int size = X.size();
    assert size == Y.size();
    List<Map<String, Double>> XX = new ArrayList<>();
    List<String> YY = new ArrayList<>();
    IntStream.range(0, size).forEach(i -> {
      Collection<String> y = Y.get(i);
      YY.addAll(y);
      XX.addAll(Collections.nCopies(y.size(), X.get(i)));
    });
    train(XX, YY, resampleType, crossValidation);
  }

  default void train(List<Map<String, Double>> X, List<String> Y, ResampleType resampleType,
          boolean crossValidation) throws AnalysisEngineProcessException {
    switch (resampleType) {
      case DOWN: {
        Map<String, Long> y2count = Y.stream().collect(groupingBy(Function.identity(), counting()));
        double yMin = Collections.min(y2count.values());
        Map<String, Double> y2weight = y2count.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> yMin / entry.getValue()));
        Set<Integer> indexes = IntStream.range(0, Y.size())
                .filter(i -> Math.random() < y2weight.get(Y.get(i))).boxed().collect(toSet());
        List<Map<String, Double>> XS = indexes.stream().map(X::get).collect(toList());
        List<String> YS = indexes.stream().map(Y::get).collect(toList());
        train(XS, YS, crossValidation);
        break;
      }
      case UP: {
        Map<String, Long> y2count = Y.stream().collect(groupingBy(Function.identity(), counting()));
        double yMax = Collections.max(y2count.values());
        Map<String, Double> y2weight = y2count.entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> yMax / entry.getValue()));
        Multiset<Integer> indexes = HashMultiset.create();
        // base "integer" count "up-sampling"
        IntStream.range(0, Y.size())
                .forEach(i -> indexes.setCount(i, y2weight.get(Y.get(i)).intValue()));
        // additional "decimal" part count
        IntStream.range(0, Y.size()).filter(i -> Math.random() < y2weight.get(Y.get(i)) % 1)
                .forEach(indexes::add);
        List<Map<String, Double>> XS = indexes.stream().map(X::get).collect(toList());
        List<String> YS = indexes.stream().map(Y::get).collect(toList());
        train(XS, YS, crossValidation);
        break;
      }
      case NONE: {
        train(X, Y, crossValidation);
        break;
      }
    }
  }

  default List<Double> crossTrainInferMultiLabel(List<Map<String, Double>> X,
          List<Collection<String>> Y, ResampleType resampleType, String label)
          throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    int nfolds = (int) Math.ceil(indexList.size() / 10.0);
    List<Double> ret = IntStream.range(0, X.size()).mapToObj(i -> Double.NaN).collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, nfolds)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<Collection<String>> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      trainMultiLabel(cvTrainX, cvTrainY, resampleType, false);
      for (int cvTestIndex : cvTestIndexes) {
        double result = infer(X.get(cvTestIndex), label);
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<List<String>> crossTrainPredictMultiLabel(List<Map<String, Double>> X,
          List<Collection<String>> Y, ResampleType resampleType, int limit)
          throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    int nfolds = (int) Math.ceil(indexList.size() / 10.0);
    List<List<String>> ret = IntStream.range(0, X.size()).mapToObj(i -> new ArrayList<String>())
            .collect(toList());
    int fold = 1;
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, nfolds)) {
      LOG.info("Train Predict Fold {}", fold++);
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<Collection<String>> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      trainMultiLabel(cvTrainX, cvTrainY, resampleType, false);
      for (int cvTestIndex : cvTestIndexes) {
        List<String> result = predict(X.get(cvTestIndex), limit).stream().collect(toList());
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<Double> crossTrainInfer(List<Map<String, Double>> X, List<String> Y,
          ResampleType resampleType, String label) throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    int nfolds = (int) Math.ceil(indexList.size() / 10.0);
    List<Double> ret = IntStream.range(0, X.size()).mapToObj(i -> Double.NaN).collect(toList());
    int fold = 1;
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, nfolds)) {
      LOG.info("Train Predict Fold {}", fold++);
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<String> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      train(cvTrainX, cvTrainY, resampleType, false);
      for (int cvTestIndex : cvTestIndexes) {
        double result = infer(X.get(cvTestIndex), label);
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<List<String>> crossTrainPredict(List<Map<String, Double>> X, List<String> Y,
          ResampleType resampleType, int limit) throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    int nfolds = (int) Math.ceil(indexList.size() / 10.0);
    List<List<String>> ret = IntStream.range(0, X.size()).mapToObj(i -> new ArrayList<String>())
            .collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, nfolds)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<String> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      train(cvTrainX, cvTrainY, resampleType, false);
      for (int cvTestIndex : cvTestIndexes) {
        List<String> result = predict(X.get(cvTestIndex), limit).stream().collect(toList());
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  static List<String> featureNames(List<Map<String, Double>> X) {
    return X.stream().map(Map::keySet).flatMap(Set::stream).distinct().collect(toList());
  }

  static Map<Integer, String> createFeatureIdKeyMap(List<Map<String, Double>> X) {
    List<String> feats = featureNames(X);
    return IntStream.range(0, feats.size()).boxed().collect(toMap(i -> i + 1, feats::get));
  }

  static List<String> featureNames(List<Map<String, Double>> X, int frequencyThreshold) {
    Map<String, Long> feat2count = X.stream().map(Map::entrySet).flatMap(Set::stream)
            .collect(groupingBy(Map.Entry::getKey, counting()));
    return feat2count.entrySet().stream().filter(e -> e.getValue() >= frequencyThreshold)
            .map(Map.Entry::getKey).collect(toList());
  }

  static List<String> labelNames(List<String> Y) {
    return Y.stream().distinct().collect(toList());
  }

  static BiMap<Integer, String> createLabelIdKeyMap(List<String> Y) {
    List<String> labels = labelNames(Y);
    BiMap<Integer, String> lid2label = HashBiMap.create();
    IntStream.range(0, labels.size()).forEach(i -> lid2label.put(i + 1, labels.get(i)));
    return lid2label;
  }

  static void saveIdKeyMap(Map<Integer, String> id2key, File idKeyMapFile) throws IOException {
    String lines = id2key.entrySet().stream().map(entry -> entry.getKey() + "\t" + entry.getValue())
            .collect(joining("\n"));
    Files.write(lines, idKeyMapFile, Charsets.UTF_8);
  }

  static Map<Integer, String> loadIdKeyMap(File idKeyMapFile) throws IOException {
    return Files.readLines(idKeyMapFile, Charsets.UTF_8).stream().map(line -> line.split("\t"))
            .collect(toMap(segs -> Integer.parseInt(segs[0]), segs -> segs[1]));
  }

}
