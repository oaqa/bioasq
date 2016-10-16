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

package edu.cmu.lti.oaqa.baseqa.answer.yesno;

import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Collections;

/**
 * This dummy <tt>YESNO</tt> answerer always creates an answer of "yes" regardless of the question.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/5/16
 */
public class AllYesYesNoAnswerPredictor extends JCasAnnotator_ImplBase {

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    TypeFactory.createAnswer(jcas, Collections.singletonList("yes")).addToIndexes();
  }

}
