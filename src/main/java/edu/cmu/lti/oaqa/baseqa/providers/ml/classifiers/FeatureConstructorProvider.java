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

package edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers;

import java.util.Map;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

/**
 * An interface that creates a feature vector from an input {@link JCas}.
 *
 * TODO: To be migrated to {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer},
 * where a candidate is given in the method
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer#score(JCas, Object)}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.Scorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/15
 */
public interface FeatureConstructorProvider extends Resource {

  Map<String, Double> constructFeatures(JCas jcas);

}
