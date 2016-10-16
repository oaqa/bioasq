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
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * A generic prediction file loader based on CV trainer {@link ClassifierTrainer}.
 * The file is specified in the parameter <tt>cv-predict-file</tt>, and the candidate type is
 * specified by a {@link CandidateProvider} via the parameter <tt>candidate-provider</tt>.
 *
 * @see ClassifierTrainer
 * @see CandidateProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> <br> created on 10/6/16
 */
public class CVPredictLoader<T> extends JCasAnnotator_ImplBase {

  private CandidateProvider candidateProvider;

  private Table<String, String, Double> qid2uri2score;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String candidateProviderName = UimaContextHelper
            .getConfigParameterStringValue(context, "candidate-provider");
    candidateProvider = ProviderCache.getProvider(candidateProviderName, CandidateProvider.class);
    // load cv
    String cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context,
            "cv-predict-file");
    List<String> lines;
    try {
      lines = Resources.readLines(getClass().getResource(cvPredictFile), Charsets.UTF_8);
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    qid2uri2score = HashBasedTable.create();
    lines.stream().map(line -> line.split("\t"))
            .forEach(segs -> qid2uri2score.put(segs[0], segs[1], Double.parseDouble(segs[2])));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String qid = TypeUtil.getQuestion(jcas).getId();
    Map<String, Double> uri2score = qid2uri2score.row(qid);
    Collection<T> candidates = candidateProvider.getCandidates(jcas);
    int rank = 0;
    for (T candidate : candidates) {
      double score = uri2score.getOrDefault(candidateProvider.getUri(candidate), 0.0);
      candidateProvider.setScoreRank(candidate, score, rank++);
    }
  }

}
