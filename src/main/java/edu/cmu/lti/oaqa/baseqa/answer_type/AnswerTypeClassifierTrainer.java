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

package edu.cmu.lti.oaqa.baseqa.answer_type;

import java.io.*;
import java.util.*;
import java.util.function.Function;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import com.google.gson.Gson;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.FeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.util.TypeUtil;

import static java.util.stream.Collectors.*;

/**
 * A classifier trainer for {@link edu.cmu.lti.oaqa.type.answer.AnswerType} prediction. The
 * features are provided from {@link FeatureConstructorProvider} and specified via the parameter
 * <tt>feature-constructor</tt>, and a {@link ClassifierProvider} specified via the parameter
 * <tt>classifier</tt>.
 * The same {@link FeatureConstructorProvider} and {@link ClassifierProvider} should be used in the
 * corresponding {@link AnswerTypeClassifierPredictor}.
 *
 * TODO: Once the {@link FeatureConstructorProvider} is migrated to the standard
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer} interface, this class could be merged
 * with {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/17/15
 */
public class AnswerTypeClassifierTrainer extends JCasAnnotator_ImplBase {

  private FeatureConstructorProvider featureConstructor;

  private ClassifierProvider classifier;

  private Map<String, Set<String>> qid2labels;

  private String cvPredictFile;

  private List<Map<String, Double>> trainX;

  private List<Collection<String>> trainY;

  private List<String> qids;

  private int limit;

  private static ClassifierProvider.ResampleType RESAMPLE_TYPE = ClassifierProvider.ResampleType.NONE;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    // feature constructor and classifier
    String featureConstructorName = UimaContextHelper.getConfigParameterStringValue(context,
            "feature-constructor");
    featureConstructor = ProviderCache.getProvider(featureConstructorName,
            FeatureConstructorProvider.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    // labels for training instances
    String[] atGsLabelFiles = UimaContextHelper
            .getConfigParameterStringArrayValue(context, "at-gslabel-files");
    Gson gson = QuestionAnswerTypes.getGson();
    Collection<QuestionAnswerTypes> qats = Arrays.stream(atGsLabelFiles).map(atGsLabelFile -> {
      Reader reader = new InputStreamReader(getClass().getResourceAsStream(atGsLabelFile));
      return gson.fromJson(reader, QuestionAnswerTypes[].class);
    }).flatMap(Arrays::stream).collect(toMap(QuestionAnswerTypes::getQid, Function.identity(),
            QuestionAnswerTypes::addQuestionAnswerTypes)).values();
    boolean nullType = UimaContextHelper
            .getConfigParameterBooleanValue(context, "null-type", false);
    float typeRatioThreshold = UimaContextHelper
            .getConfigParameterFloatValue(context, "type-ratio-threshold", 0.5f);
    qid2labels = qats.stream().collect(toMap(QuestionAnswerTypes::getQid,
            qat -> qat.getTypeRatios(nullType).entrySet().stream()
                    .filter(e -> e.getValue() >= typeRatioThreshold).map(Map.Entry::getKey)
                    .collect(toSet())
    ));
    // cv file
    cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context, "cv-predict-file",
            null);
    trainX = new ArrayList<>();
    trainY = new ArrayList<>();
    if (cvPredictFile != null) {
      qids = new ArrayList<>();
    }
    limit = UimaContextHelper.getConfigParameterIntValue(context, "cv-predict-limit", 1);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Map<String, Double> features = featureConstructor.constructFeatures(jcas);
    trainX.add(features);
    String qid = TypeUtil.getQuestion(jcas).getId();
    trainY.add(qid2labels.get(qid));
    if (cvPredictFile != null) {
      qids.add(qid);
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (cvPredictFile != null) {
      try (BufferedWriter bw = Files.newWriter(new File(cvPredictFile), Charsets.UTF_8)) {
        Set<Double> f1s = new HashSet<>();
        List<List<String>> results = classifier
                .crossTrainPredictMultiLabel(trainX, trainY, RESAMPLE_TYPE, limit);
        for (int i = 0; i < qids.size(); i++) {
          String qid = qids.get(i);
          List<String> predLabels = results.get(i);
          // calculate f1
          Set<String> gsLabels = qid2labels.get(qid);
          f1s.add(2.0 * Sets.intersection(gsLabels, ImmutableSet.copyOf(predLabels)).size() /
                  (gsLabels.size() + predLabels.size()));
          // write to file
          bw.write(qid + "\t" + predLabels.stream().collect(joining(";")) + "\n");
        }
        f1s.stream().mapToDouble(Double::doubleValue).average()
                .ifPresent(f1 -> System.out.println("Micro F1: " + f1));
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    System.out.println("Train Classifier");
    // changed CV to false, as a "micro f1" will be calculated if the cvPredictFile is specifie
    classifier.trainMultiLabel(trainX, trainY, RESAMPLE_TYPE, false);
  }

}
