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

package edu.cmu.lti.oaqa.baseqa.evidence.parse;

import edu.cmu.lti.oaqa.baseqa.providers.parser.ParserProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.List;

/**
 * This {@link JCasAnnotator_ImplBase} uses a {@link ParserProvider} to parse and annotate the
 * passages residing in the individual views.
 *
 * @see ParserProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/12/15
 */
public class PassageParser extends JCasAnnotator_ImplBase {

  private ParserProvider parserProvider;

  private String viewNamePrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String parserProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "parser-provider");
    parserProvider = ProviderCache.getProvider(parserProviderName, ParserProvider.class);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    ViewType.listViews(jcas, viewNamePrefix).stream().map(parserProvider::parseDependency)
            .flatMap(List::stream).forEach(Token::addToIndexes);
  }

}
