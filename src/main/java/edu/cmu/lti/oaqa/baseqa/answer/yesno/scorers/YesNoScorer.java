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

package edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers;

import com.google.common.collect.ImmutableMap;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import java.util.Collection;
import java.util.Map;

/**
 * <p>
 *   An implementation of this interface can evidence how likely a <tt>YESNO</tt> question is true
 *   by providing one or many feature values. The implementations should be integrated into
 *   {@link edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerTrainer} and
 *   {@link edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerPredictor} as <tt>scorers</tt>.
 * </p>
 * <p>
 *   TODO: In a future version, this interface may be merged with the general
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer} interface for arbitrary classification
 *   tasks, once the {@link edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerTrainer} and
 *   {@link edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerPredictor} are merged with
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer} and
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerTrainer
 * @see edu.cmu.lti.oaqa.baseqa.answer.yesno.YesNoAnswerPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.Scorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public interface YesNoScorer extends Resource {

  Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException;

  static ImmutableMap<String, Double> aggregateFeatures(Collection<? extends Number> values,
          String keyword) {
    ImmutableMap.Builder<String, Double> features = ImmutableMap.builder();
    features.put(keyword + "-sum",
            values.stream().mapToDouble(Number::doubleValue).sum());
    features.put(keyword + "-avg",
            values.stream().mapToDouble(Number::doubleValue).average().orElse(0));
    features.put(keyword + "-max",
            values.stream().mapToDouble(Number::doubleValue).max().orElse(0));
    features.put(keyword + "-min",
            values.stream().mapToDouble(Number::doubleValue).min().orElse(0));
    return features.build();
  }

}
