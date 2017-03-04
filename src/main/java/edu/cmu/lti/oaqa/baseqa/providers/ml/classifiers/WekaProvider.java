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

import com.google.common.collect.HashMultiset;
import com.google.common.collect.Iterables;
import com.google.common.collect.Multiset;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.core.*;
import weka.core.converters.ArffSaver;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   A {@link ClassifierProvider} that wraps <a href="http://www.cs.waikato.ac.nz/ml/weka/">Weka</a>
 *   classifiers.
 *   A descriptor of this {@link ConfigurableProvider} should specify the actual classifier name
 *   (full class path) via <tt>classifier-name</tt> parameter.
 * </p>
 * <p>
 *   Other parameters include <tt>model-file</tt>, <tt>dataset-schema-file</tt>,
 *   <tt>dataset-export</tt>, etc.
 * </p>
 * <p>
 *   Note that Weka is licensed under PDL!
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/8/15
 */
public class WekaProvider extends ConfigurableProvider implements ClassifierProvider {

  private File modelFile;

  private File datasetSchemaFile;

  private Classifier classifier;

  private Instances datasetSchema;

  private File datasetExportFile;

  private String classifierName;

  private String[] options;

  private boolean balanceWeight;

  private static final Logger LOG = LoggerFactory.getLogger(WekaProvider.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // model
    if ((modelFile = new File((String) getParameterValue("model-file"))).exists()) {
      try {
        classifier = (Classifier) SerializationHelper.read(modelFile.getAbsolutePath());
      } catch (Exception e) {
        throw new ResourceInitializationException(e);
      }
    }
    // dataset schema
    if ((datasetSchemaFile = new File((String) getParameterValue("dataset-schema-file"))).exists()) {
      try {
        datasetSchema = (Instances) SerializationHelper.read(datasetSchemaFile.getAbsolutePath());
      } catch (Exception e) {
        throw new ResourceInitializationException(e);
      }
    }
    // training instances backup as arff
    Object datasetExport;
    if ((datasetExport = getParameterValue("dataset-export")) != null) {
      datasetExportFile = new File(String.class.cast(datasetExport));
    }
    // classifier
    classifierName = String.class.cast(getParameterValue("classifier-name"));
    options = Iterables.toArray((Iterable<String>) getParameterValue("options"), String.class);
    balanceWeight = (boolean) getParameterValue("balance-weight");
    return ret;
  }

  @Override
  public Map<String, Double> infer(Map<String, Double> features)
          throws AnalysisEngineProcessException {
    Instances testInstances = new Instances(datasetSchema, 1);
    Instance instance = newInstance(features, null, 1.0, testInstances);
    double[] probs;
    try {
      probs = classifier.distributionForInstance(instance);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    return IntStream.range(0, probs.length).boxed()
            .collect(toMap(i -> datasetSchema.classAttribute().value(i), i -> probs[i]));
  }

  @Override
  public void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException {
    // create attribute (including label) info
    ArrayList<Attribute> attributes = new ArrayList<>();
    ClassifierProvider.featureNames(X).stream().map(Attribute::new)
            .forEachOrdered(attributes::add);
    Attribute label = new Attribute("__label__", ClassifierProvider.labelNames(Y));
    attributes.add(label);
    String name = Files.getNameWithoutExtension(modelFile.getName());
    datasetSchema = new Instances(name, attributes, X.size());
    datasetSchema.setClass(label);
    // add instances
    Instances trainingInstances = new Instances(datasetSchema, X.size());
    if (balanceWeight) {
      Multiset<String> labelCounts = HashMultiset.create(Y);
      double maxCount = labelCounts.entrySet().stream().mapToInt(Multiset.Entry::getCount).max()
              .orElseThrow(AnalysisEngineProcessException::new);
      for (int i = 0; i < X.size(); i++) {
        String y = Y.get(i);
        double weight = maxCount / labelCounts.count(y);
        trainingInstances.add(newInstance(X.get(i), y, weight, trainingInstances));
      }
    } else {
      for (int i = 0; i < X.size(); i++) {
        trainingInstances.add(newInstance(X.get(i), Y.get(i), 1.0, trainingInstances));
      }
    }
    // training
    try {
      classifier = AbstractClassifier.forName(classifierName, options);
      classifier.buildClassifier(trainingInstances);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    // write model and dataset schema
    try {
      SerializationHelper.write(modelFile.getAbsolutePath(), classifier);
      SerializationHelper.write(datasetSchemaFile.getAbsolutePath(), datasetSchema);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    // backup training dataset as arff file
    if (datasetExportFile != null) {
      try {
        ArffSaver saver = new ArffSaver();
        saver.setInstances(trainingInstances);
        saver.setFile(datasetExportFile);
        saver.writeBatch();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    if (crossValidation) {
      try {
        Evaluation eval = new Evaluation(trainingInstances);
        Random rand = new Random();
        eval.crossValidateModel(classifier, trainingInstances, 10, rand);
        LOG.debug(eval.toSummaryString());
      } catch (Exception e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

  private static Instance newInstance(Map<String, Double> features, String label, double weight,
          Instances dataset) {
    double[] values = new double[dataset.numAttributes()];
    for (Map.Entry<String, Double> entry : features.entrySet()) {
      Attribute attribute = dataset.attribute(entry.getKey());
      if (attribute == null) continue;
      values[attribute.index()] = entry.getValue();
    }
    SparseInstance instance = new SparseInstance(weight, values);
    instance.setDataset(dataset);
    if (label != null)
      instance.setClassValue(label);
    return instance;
  }

}
