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

package edu.cmu.lti.oaqa.baseqa.answer.modify.modifiers;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

/**
 * An interface that modifies existing {@link edu.cmu.lti.oaqa.type.answer.Answer} for certain types
 * of inputs, pluggable into {@link edu.cmu.lti.oaqa.baseqa.answer.modify.AnswerModificationManager}
 * to get executed.
 *
 * @see edu.cmu.lti.oaqa.baseqa.answer.modify.AnswerModificationManager
 * @see CavModifier
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/1/15
 */
public interface AnswerModifier extends Resource {

  boolean accept(JCas jcas) throws AnalysisEngineProcessException;

  void modify(JCas jcas) throws AnalysisEngineProcessException;

}
