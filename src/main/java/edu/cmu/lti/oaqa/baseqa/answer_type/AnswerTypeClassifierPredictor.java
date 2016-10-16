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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.FeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * A predictor for {@link edu.cmu.lti.oaqa.type.answer.AnswerType}.
 * The features are provided from {@link FeatureConstructorProvider} and specified via the parameter
 * <tt>feature-constructor</tt>, and a {@link ClassifierProvider} specified via the parameter
 * <tt>classifier</tt>.
 * The same {@link FeatureConstructorProvider} and {@link ClassifierProvider} should be used in the
 * corresponding {@link AnswerTypeClassifierTrainer}.
 *
 * TODO: Once the {@link FeatureConstructorProvider} is migrated to the standard
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer} interface, this class could be merged
 * with {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/17/15
 */
public class AnswerTypeClassifierPredictor extends JCasAnnotator_ImplBase {

  private FeatureConstructorProvider featureConstructor;

  private ClassifierProvider classifier;

  private BufferedWriter predictFileWriter;

  private int limit;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String featureConstructorName = UimaContextHelper.getConfigParameterStringValue(context,
            "feature-constructor");
    featureConstructor = ProviderCache.getProvider(featureConstructorName,
            FeatureConstructorProvider.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    String predictFilename = UimaContextHelper.getConfigParameterStringValue(context,
            "predict-file", null);
    limit = UimaContextHelper.getConfigParameterIntValue(context, "limit", 1);
    if (predictFilename != null) {
      try {
        predictFileWriter = Files.newWriter(new File(predictFilename), Charsets.UTF_8);
      } catch (FileNotFoundException e) {
        throw new ResourceInitializationException(e);
      }
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // load data
    Map<String, Double> features = featureConstructor.constructFeatures(jcas);
    // predication
    List<String> lats = classifier.predict(features, limit);
    lats.stream().map(lat -> TypeFactory.createLexicalAnswerType(jcas, lat))
            .forEachOrdered(LexicalAnswerType::addToIndexes);
    String question = TypeUtil.getQuestion(jcas).getText().trim().replaceAll("\\s", " ")
            .replaceAll("–", "-").replaceAll("’", "'");
    System.out.println("Found answer type: " + lats);
    // print to file if exists
    if (predictFileWriter != null) {
      try {
        predictFileWriter.write(question + "\t" + lats + "\n");
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (predictFileWriter != null) {
      try {
        predictFileWriter.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

}
