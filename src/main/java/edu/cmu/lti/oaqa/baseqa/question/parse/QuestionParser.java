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

package edu.cmu.lti.oaqa.baseqa.question.parse;

import edu.cmu.lti.oaqa.baseqa.providers.parser.ParserProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.Token;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

/**
 * This {@link JCasAnnotator_ImplBase} uses a {@link ParserProvider} to parse and annotate the
 * question in the main view.
 *
 * @see ParserProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/12/15
 */
public class QuestionParser extends JCasAnnotator_ImplBase {

  private ParserProvider parserProvider;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String nlpProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "parser-provider");
    parserProvider = ProviderCache.getProvider(nlpProviderName, ParserProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    parserProvider.parseDependency(jcas).forEach(Token::addToIndexes);
  }

}
