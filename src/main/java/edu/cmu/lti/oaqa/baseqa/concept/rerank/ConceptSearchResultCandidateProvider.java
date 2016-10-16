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

package edu.cmu.lti.oaqa.baseqa.concept.rerank;

import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractCandidateProvider;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;

/**
 * An {@link AbstractCandidateProvider} for {@link ConceptSearchResult}, used in relevant concept
 * reranking training (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}),
 * relevant concept prediction (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}), and cross-validation
 * prediction loading (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}).
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/9/16
 */
public class ConceptSearchResultCandidateProvider
        extends AbstractCandidateProvider<ConceptSearchResult> {

  @Override
  public Collection<ConceptSearchResult> getCandidates(JCas jcas) {
    return TypeUtil.getRankedConceptSearchResults(jcas);
  }

  @Override
  public void setScoreRank(ConceptSearchResult candidate, double score, int rank) {
    candidate.setScore(score);
    candidate.setRank(rank);
  }

  @Override
  public Collection<ConceptSearchResult> getGoldStandards(JCas jcas) {
    return TypeUtil.getRankedConceptSearchResults(ViewType.getGsView(jcas));
  }

  @Override
  public boolean match(ConceptSearchResult candidate, Collection<ConceptSearchResult> gs) {
    return gs.stream().map(ConceptSearchResult::getUri)
            .anyMatch(gsUri -> candidate.getUri().equals(gsUri));
  }

  @Override
  public String getUri(ConceptSearchResult candidate) {
    return candidate.getUri();
  }

  @Override
  public String toString(ConceptSearchResult candidate) {
    return TypeUtil.toString(candidate);
  }

}
