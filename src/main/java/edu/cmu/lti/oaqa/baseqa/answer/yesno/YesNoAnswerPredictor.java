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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers.YesNoScorer;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.ecd.phase.ProcessingStepUtils;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.joining;

/**
 * This class considers the yes/no question answering problem as a binary classification problem,
 * and allows to integrate various sources of evidence ({@link YesNoScorer}) and a
 * {@link ClassifierProvider} of the user's choice into the system.
 * This is the prediction {@link JCasAnnotator_ImplBase}, and the corresponding
 * {@link YesNoAnswerTrainer} should use the same set of ({@link YesNoScorer}) and the
 * {@link ClassifierProvider}.
 *
 * TODO: A future version may be merged with {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}.
 *
 * @see YesNoAnswerTrainer
 * @see YesNoScorer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/21/16
 */
public class YesNoAnswerPredictor extends JCasAnnotator_ImplBase {

  private List<YesNoScorer> scorers;

  private ClassifierProvider classifier;

  private String featureFilename;

  private Table<String, String, Double> feat2value;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, YesNoScorer.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    featureFilename = UimaContextHelper.getConfigParameterStringValue(context, "feature-file",
            null);
    if (featureFilename != null) {
      feat2value = HashBasedTable.create();
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String qid = null;
    if (featureFilename != null) {
      qid = ProcessingStepUtils.getSequenceId(jcas);
    }
    Map<String, Double> features = new HashMap<>();
    for (YesNoScorer scorer : scorers) {
      features.putAll(scorer.score(jcas));
    }
    // predict
    String answer = classifier.predict(features);
    if (featureFilename != null) {
      putFeatureValues(qid, answer, features);
    }
    TypeFactory.createAnswer(jcas, Collections.singletonList(answer)).addToIndexes();
    System.out.println("Predicted answer: " + answer);
  }

  private void putFeatureValues(String qid, String answer, Map<String, Double> features) {
    String id = String.join("\t", qid, answer);
    feat2value.row(id).putAll(features);
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (featureFilename != null) {
      try {
        BufferedWriter bw = Files.newWriter(new File(featureFilename), Charsets.UTF_8);
        Set<String> feats = feat2value.columnKeySet();
        bw.write("\t\t" + feats.stream().collect(joining("\t")) + "\n");
        bw.write(feat2value.rowMap().entrySet().stream().map(e -> e.getKey() + "\t" +
                feats.stream().map(feat -> e.getValue().getOrDefault(feat, 0.0))
                        .map(String::valueOf).collect(joining("\t"))).collect(joining("\n")));
        bw.close();
      } catch (IOException ex) {
        throw new AnalysisEngineProcessException(ex);
      }
    }
  }
}
