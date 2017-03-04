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
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;
import edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.ecd.phase.ProcessingStepUtils;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * <p>A generic class for prediction.</p>
 * <p>
 *   The type of instance (e.g. answer type, relevant document, concept, etc.) to be classified is
 *   specified via the <tt>candidate-provider</tt> parameter.
 *   Feature extractors of type {@link Scorer} are specified via the <tt>scorers</tt> parameter.
 *   Classifier of type {@link ClassifierProvider} is specified via the <tt>classifier</tt>
 *   parameter.
 * </p>
 * <p>
 *   The corresponding {@link ClassifierTrainer} should use the same set of
 *   {@link CandidateProvider}, {@link ClassifierProvider}, and {@link Scorer}s.
 * </p>
 *
 * @see ClassifierTrainer
 * @see CandidateProvider
 * @see ClassifierProvider
 * @see Scorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/9/16
 */
public class ClassifierPredictor<T> extends JCasAnnotator_ImplBase {

  private CandidateProvider<T> candidateProvider;

  private List<Scorer<? super T>> scorers;

  private ClassifierProvider classifier;

  private String featureFilename;

  private Table<String, String, Double> feat2value;

  private static final Logger LOG = LoggerFactory.getLogger(ClassifierPredictor.class);

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
    if ((featureFilename = UimaContextHelper.getConfigParameterStringValue(context, "feature-file",
            null)) != null) {
      feat2value = HashBasedTable.create();
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<T> results = candidateProvider.getCandidates(jcas);
    String qid = null;
    if (featureFilename != null) {
      qid = ProcessingStepUtils.getSequenceId(jcas);
    }
    for (Scorer<? super T> scorer : scorers) {
      scorer.prepare(jcas);
    }
    Map<T, Double> result2score = new HashMap<>();
    for (T result : results) {
      Map<String, Double> features = new HashMap<>();
      scorers.stream().map(scorer -> scorer.score(jcas, result)).forEach(features::putAll);
      double score = classifier.infer(features, "true");
      result2score.put(result, score);
      if (featureFilename != null) {
        putFeatureValues(qid, result, score, features);
      }
    }
    List<Map.Entry<T, Double>> sorted = result2score.entrySet().stream()
            .sorted(Comparator.comparing(Map.Entry<T, Double>::getValue).reversed())
            .collect(toList());
    IntStream.range(0, sorted.size()).forEach(rank -> candidateProvider
            .setScoreRank(sorted.get(rank).getKey(), sorted.get(rank).getValue(), rank));
    if (LOG.isInfoEnabled()) {
      LOG.info("Top scored candidates: ");
      sorted.stream().map(Map.Entry::getKey).limit(10)
              .forEachOrdered(c -> LOG.info(" - {}", candidateProvider.toString(c)));
    }
  }

  private void putFeatureValues(String qid, T result, double score,
          Map<String, Double> features) {
    String uri = candidateProvider.getUri(result);
    String id = String.join("\t", qid, uri, String.valueOf(score));
    feat2value.row(id).putAll(features);
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (featureFilename != null) {
      try (BufferedWriter bw = Files.newWriter(new File(featureFilename), Charsets.UTF_8)) {
        Set<String> feats = feat2value.columnKeySet();
        bw.write("\t\t\t" + String.join("\t", feats) + "\n");
        for (Map.Entry<String, Map<String, Double>> id2map : feat2value.rowMap().entrySet()) {
          bw.write(id2map.getKey());
          for (Map.Entry<String, Double> feat2value : id2map.getValue().entrySet()) {
            bw.write("\t");
            bw.write(format(feat2value.getValue(), 4));
          }
          bw.newLine();
          bw.flush();
        }
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

  // Fast double to string conversion with given precision, see
  // http://stackoverflow.com/a/10554128
  private static final int POW10[] = {1, 10, 100, 1000, 10000, 100000, 1000000};

  public static String format(double val, int precision) {
    StringBuilder sb = new StringBuilder();
    if (val < 0) {
      sb.append('-');
      val = -val;
    }
    int exp = POW10[precision];
    long lval = (long)(val * exp + 0.5);
    sb.append(lval / exp).append('.');
    long fval = lval % exp;
    for (int p = precision - 1; p > 0 && fval < POW10[p]; p--) {
      sb.append('0');
    }
    sb.append(fval);
    return sb.toString();
  }

}
