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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.SetMultimap;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import meka.classifiers.multilabel.MultiLabelClassifier;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import weka.classifiers.AbstractClassifier;
import weka.classifiers.Evaluation;
import weka.core.*;

import java.io.File;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   A {@link ClassifierProvider} that wraps <a href="http://meka.sourceforge.net/">Meka</a>
 *   classifiers -- multilabel extension to Weka.
 *   A descriptor of this {@link ConfigurableProvider} should specify the actual classifier name
 *   (full class path) via <tt>classifier-name</tt> parameter.
 * </p>
 * <p>
 *   Parameters are similar to {@link WekaProvider}.
 * </p>
 * <p>
 *   Note that Weka is licensed under PDL!
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/8/15
 */
public class MekaProvider extends ConfigurableProvider implements ClassifierProvider {

  private File modelFile;

  private File datasetSchemaFile;

  private MultiLabelClassifier classifier;

  private String classifierName;

  private String[] options;

  private Instances datasetSchema;

  private static final Logger LOG = LoggerFactory.getLogger(MekaProvider.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // model
    if ((modelFile = new File((String) getParameterValue("model-file"))).exists()) {
      try {
        classifier = (MultiLabelClassifier) SerializationHelper.read(modelFile.getAbsolutePath());
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
    // classifier
    classifierName = String.class.cast(getParameterValue("classifier-name"));
    //noinspection unchecked
    options = Iterables.toArray((Iterable<String>) getParameterValue("options"), String.class);
    return ret;
  }

  @Override
  public Map<String, Double> infer(Map<String, Double> features)
          throws AnalysisEngineProcessException {
    Instance instance = new SparseInstance(features.size());
    instance.setDataset(datasetSchema);
    for (Map.Entry<String, Double> e : features.entrySet()) {
      Attribute attribute = datasetSchema.attribute(e.getKey());
      if (attribute == null) continue;
      instance.setValue(attribute, e.getValue());
    }
    double[] probs;
    try {
      probs = classifier.distributionForInstance(instance);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    assert datasetSchema.classIndex() == probs.length;
    return IntStream.range(0, probs.length).boxed()
            .collect(toMap(i -> datasetSchema.attribute(i).name(), i -> probs[i]));
  }

  @Override
  public void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException {
    // create attribute (including label) info
    ArrayList<Attribute> attributes = new ArrayList<>();
    List<String> labelNames = ClassifierProvider.labelNames(Y);
    labelNames.stream().map(attr -> new Attribute(attr, Arrays.asList("y", "n")))
            .forEachOrdered(attributes::add);
    List<String> featureNames = ClassifierProvider.featureNames(X);
    featureNames.stream().map(Attribute::new).forEachOrdered(attributes::add);
    String name = Files.getNameWithoutExtension(modelFile.getName());
    datasetSchema = new Instances(name, attributes, 0);
    datasetSchema.setClassIndex(labelNames.size());
    // add instances
    // due to the limitation of the interface definition, X, Y should be reorganized
    SetMultimap<Map<String, Double>, String> XY = HashMultimap.create();
    IntStream.range(0, X.size()).forEach(i -> XY.put(X.get(i), Y.get(i)));
    Instances trainingInstances = new Instances(datasetSchema, XY.size());
    for (Map.Entry<Map<String, Double>, Collection<String>> entry : XY.asMap().entrySet()) {
      Set<String> y = ImmutableSet.copyOf(entry.getValue());
      Map<String, Double> x = entry.getKey();
      SparseInstance instance = new SparseInstance(labelNames.size() + x.size());
      for (String labelName : labelNames) {
        instance.setValue(datasetSchema.attribute(labelName), y.contains(labelName) ? "y" : "n");
      }
      for (Map.Entry<String, Double> e : x.entrySet()) {
        instance.setValue(datasetSchema.attribute(e.getKey()), e.getValue());
      }
      trainingInstances.add(instance);
    }
    // training
    try {
      classifier = (MultiLabelClassifier) AbstractClassifier.forName(classifierName, options);
      classifier.buildClassifier(trainingInstances);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    try {
      SerializationHelper.write(modelFile.getAbsolutePath(), classifier);
      SerializationHelper.write(datasetSchemaFile.getAbsolutePath(), datasetSchema);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
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

}
