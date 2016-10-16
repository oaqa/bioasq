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

package edu.cmu.lti.oaqa.baseqa.document.rerank;

import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractCandidateProvider;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;

/**
 * An {@link AbstractCandidateProvider} for {@link Document}, used in relevant document reranking
 * training (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}), relevant
 * document prediction (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}), and
 * cross-validation prediction loading (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}).
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader
 *
 * @author @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/10/16
 */
public class DocumentCandidateProvider extends AbstractCandidateProvider<Document> {

  @Override
  public Collection<Document> getCandidates(JCas jcas) {
    return TypeUtil.getRankedDocuments(jcas);
  }

  @Override
  public void setScoreRank(Document candidate, double score, int rank) {
    candidate.setScore(score);
    candidate.setRank(rank);
  }

  @Override
  public Collection<Document> getGoldStandards(JCas jcas) {
    return TypeUtil.getRankedDocuments(ViewType.getGsView(jcas));
  }

  @Override
  public boolean match(Document candidate, Collection<Document> gs) {
    return gs.stream().map(Document::getUri)
            .anyMatch(gsUri -> candidate.getUri().equals(gsUri));
  }

  @Override
  public String getUri(Document candidate) {
    return candidate.getUri();
  }

  @Override
  public String toString(Document candidate) {
    return TypeUtil.toString(candidate);
  }

}
