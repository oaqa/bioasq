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

package edu.cmu.lti.oaqa.baseqa.passage.rerank;

import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractCandidateProvider;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;
import edu.stanford.nlp.util.StringUtils;
import org.apache.uima.jcas.JCas;

import java.util.Collection;

/**
 * An {@link AbstractCandidateProvider} for {@link Passage}, used in relevant passage reranking
 * training (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}), relevant
 * passage prediction (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}), and
 * cross-validation prediction loading (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}).
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/10/16
 */
public class PassageCandidateProvider extends AbstractCandidateProvider<Passage> {

  @Override
  public Collection<Passage> getCandidates(JCas jcas) {
    return TypeUtil.getRankedPassages(jcas);
  }

  @Override
  public void setScoreRank(Passage candidate, double score, int rank) {
    candidate.setScore(score);
    candidate.setRank(rank);
  }

  @Override
  public Collection<Passage> getGoldStandards(JCas jcas) {
    return TypeUtil.getRankedPassages(ViewType.getGsView(jcas));
  }

  @Override
  public boolean match(Passage candidate, Collection<Passage> gs) {
    return gs.parallelStream().map(Passage::getText)
            .anyMatch(gsText -> match(candidate.getText(), gsText));
  }

  private boolean match(String candidateText, String gsText) {
    return Math.max(candidateText.length(), gsText.length()) * 0.9 <
            StringUtils.longestCommonSubstring(candidateText, gsText);
  }

  @Override
  public String getUri(Passage candidate) {
    return TypeUtil.getUriOffsets(candidate, ":");
  }

  @Override
  public String toString(Passage candidate) {
    return TypeUtil.toString(candidate);
  }

}
