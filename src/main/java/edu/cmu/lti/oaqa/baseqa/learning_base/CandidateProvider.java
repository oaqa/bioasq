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

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import java.util.Collection;
import java.util.List;

/**
 * An interface that standardizes basic operations across various basic data types.
 * An implemented class can be plugged into a
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer} for classification training, a
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor} for classification predication,
 * and cross-validation prediction loading (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}).
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/9/16
 */
public interface CandidateProvider<T> extends Resource {

  Collection<T> getCandidates(JCas jcas);

  void setScoreRank(T candidate, double score, int rank);

  String getUri(T candidate);

  Collection<T> getGoldStandards(JCas jcas);

  boolean match(T candidate, Collection<T> gs);

  String toString(T candidate);

}
