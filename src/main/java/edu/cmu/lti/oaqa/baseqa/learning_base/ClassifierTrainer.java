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

import com.google.common.base.Charsets;
import com.google.common.collect.ImmutableMap;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * <p>A generic class for training a classifier.</p>
 * <p>
 *   The type of instance (e.g. answer type, relevant document, concept, etc.) to be classified is
 *   specified via the <tt>candidate-provider</tt> parameter.
 *   Feature extractors of type {@link Scorer} are specified via the <tt>scorers</tt> parameter.
 *   Classifier of type {@link ClassifierProvider} is specified via the <tt>classifier</tt>
 *   parameter.
 *   One can set the <tt>at-least-one-correct</tt> parameter to <tt>true</tt> to ignore the input
 *   that do not have any positive labels, e.g. the questions that do not have any relevant
 *   documents.
 *   As the positive and negative set can be imbalanced, one can specify the <tt>resample-type</tt>
 *   to <tt>DOWN</tt> to down-sample the larger set, <tt>UP</tt> to up-sample the smaller set,
 *   or <tt>NONE</tt> to do nothing.
 * </p>
 * <p>
 *   The corresponding {@link ClassifierPredictor} should use the same set of
 *   {@link CandidateProvider}, {@link ClassifierProvider}, and {@link Scorer}s.
 * </p>
 *
 * @see ClassifierPredictor
 * @see CandidateProvider
 * @see ClassifierProvider
 * @see Scorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/9/16
 */
public class ClassifierTrainer<T> extends JCasAnnotator_ImplBase {

  private CandidateProvider<T> candidateProvider;

  private List<Scorer<? super T>> scorers;

  private ClassifierProvider classifier;

  private String cvPredictFile;

  private List<Map<String, Double>> X;

  private List<String> Y;

  private List<String> iduris;

  private boolean atLeastOneCorrect;

  private ClassifierProvider.ResampleType resampleType;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String candidateProviderName = UimaContextHelper
            .getConfigParameterStringValue(context, "candidate-provider");
    candidateProvider = ProviderCache.getProvider(candidateProviderName, CandidateProvider.class);
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, Scorer.class).stream()
            .map(scorer -> (Scorer<? super T>) scorer).collect(toList());
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    cvPredictFile = UimaContextHelper
            .getConfigParameterStringValue(context, "cv-predict-file", null);
    if (cvPredictFile != null) {
      iduris = new ArrayList<>();
    }
    X = new ArrayList<>();
    Y = new ArrayList<>();
    atLeastOneCorrect = UimaContextHelper
            .getConfigParameterBooleanValue(context, "at-least-one-correct", true);
    resampleType = ClassifierProvider.ResampleType.valueOf(
            UimaContextHelper.getConfigParameterStringValue(context, "resample-type", "NONE"));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<T> gs = candidateProvider.getGoldStandards(jcas);
    Collection<T> results = candidateProvider.getCandidates(jcas);
    List<String> Ysubset = results.stream()
            .map(result -> candidateProvider.match(result, gs) ? "true" : "false")
            .collect(toList());
    if (atLeastOneCorrect && !Ysubset.contains("true")) return;
    for (Scorer<? super T> scorer : scorers) {
      scorer.prepare(jcas);
    }
    for (T result : results) {
      ImmutableMap.Builder<String, Double> features = ImmutableMap.builder();
      scorers.stream().map(scorer -> scorer.score(jcas, result)).map(Map::entrySet)
              .flatMap(Set::stream).filter(e -> e.getValue() != 0.0)
              .forEach(e -> features.put(e.getKey(), e.getValue()));
      X.add(features.build());
    }
    Y.addAll(Ysubset);
    if (cvPredictFile != null) {
      for (T result : results) {
        String uri = candidateProvider.getUri(result);
        iduris.add(TypeUtil.getQuestion(jcas).getId() + "\t" + uri);
      }
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    System.out.println("Total true count: " + Y.stream().filter("true"::equals).count());
    System.out.println("Total false count: " + Y.stream().filter("false"::equals).count());
    super.collectionProcessComplete();
    if (cvPredictFile != null) {
      try (BufferedWriter bw = Files.newWriter(new File(cvPredictFile), Charsets.UTF_8)) {
        List<Double> results = classifier.crossTrainInfer(X, Y, resampleType, "true");
        for (int i = 0; i < iduris.size(); i++) {
          bw.write(iduris.get(i) + "\t" + results.get(i) + "\n");
        }
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    classifier.train(X, Y, resampleType, true);
  }

}
