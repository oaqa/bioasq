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

package edu.cmu.lti.oaqa.baseqa.answer.generate.generators;

import java.util.List;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;

/**
 * An interface that defines a {@link CandidateAnswerVariant} provider, generating a list of
 * {@link CandidateAnswerVariant} from the input {@link JCas}. Additionally, one can specify in
 * which cases this provider is called using the {@link #accept(JCas)} method if this is designed
 * to be used by some types of questions. Each {@link CavGenerator} instance should be integrated
 * into the {@link edu.cmu.lti.oaqa.baseqa.answer.generate.CavGenerationManager}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public interface CavGenerator extends Resource {

  boolean accept(JCas jcas) throws AnalysisEngineProcessException;

  List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException;

}
