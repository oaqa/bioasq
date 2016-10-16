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

package edu.cmu.lti.oaqa.baseqa.answer.yesno;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers.YesNoScorer;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * This class considers the yes/no question answering problem as a binary classification problem,
 * and allows to integrate various sources of evidence ({@link YesNoScorer}) and a
 * {@link ClassifierProvider} of the user's choice into the system.
 * This is the training {@link JCasAnnotator_ImplBase}, and the corresponding
 * {@link YesNoAnswerPredictor} should use the same set of ({@link YesNoScorer}) and the
 * {@link ClassifierProvider}.
 *
 * TODO: A future version may be merged with {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}.
 *
 * @see YesNoAnswerPredictor
 * @see YesNoScorer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/21/16
 */
public class YesNoAnswerTrainer extends JCasAnnotator_ImplBase {

  private List<YesNoScorer> scorers;

  private ClassifierProvider classifier;

  private String cvPredictFile;

  private List<Map<String, Double>> X;

  private List<String> Y;

  private List<String> qids;

  private ClassifierProvider.ResampleType resampleType;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, YesNoScorer.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context, "cv-predict-file",
            null);
    if (cvPredictFile != null) {
      qids = new ArrayList<>();
    }
    X = new ArrayList<>();
    Y = new ArrayList<>();
    resampleType = ClassifierProvider.ResampleType.valueOf(
            UimaContextHelper.getConfigParameterStringValue(context, "resample-type", "NONE"));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Map<String, Double> features = new HashMap<>();
    for (YesNoScorer scorer : scorers) {
      features.putAll(scorer.score(jcas));
    }
    // add to training data
    X.add(features);
    Answer answer = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).get(0);
    System.out.println("answer text: " + answer.getText());
    Y.add(answer.getText());
    if (cvPredictFile != null) {
      qids.add(TypeUtil.getQuestion(jcas).getId());
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    long yesCount = Y.stream().filter(y -> y.equals("yes")).count();
    System.out.println("Total yes: " + yesCount);
    long noCount = Y.stream().filter(y -> y.equals("no")).count();
    System.out.println("Total no: " + noCount);
    super.collectionProcessComplete();
    if (cvPredictFile != null) {
      try (BufferedWriter bw = Files.newWriter(new File(cvPredictFile), Charsets.UTF_8)) {
        int correctYesCount = 0;
        int correctNoCount = 0;
        List<Double> results = classifier.crossTrainInfer(X, Y, resampleType, "yes");
        for (int i = 0; i < qids.size(); i++) {
          double result = results.get(i);
          bw.write(qids.get(i) + "\t" + result + "\n");
          String y = Y.get(i);
          if (result >= 0.5 && y.equals("yes")) correctYesCount++;
          if (result < 0.5 && y.equals("no")) correctNoCount++;
        }
        bw.close();
        System.out.println("Cross validation accuracy: ");
        System.out.println(" - Yes: " + (double) correctYesCount / yesCount);
        System.out.println(" - No: " + (double) correctNoCount / noCount);
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    classifier.train(X, Y, resampleType, false);
  }

}
