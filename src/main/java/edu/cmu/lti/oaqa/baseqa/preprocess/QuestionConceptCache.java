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

package edu.cmu.lti.oaqa.baseqa.preprocess;

import java.util.ArrayList;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * <p>
 *   A {@link JCasAnnotator_ImplBase} that scans through all questions in each input {@link JCas}
 *   and uses a list of cacheable {@link ConceptProvider}s and {@link SynonymExpansionProvider}s to
 *   annotate the questions.
 * </p>
 * <p>
 *   The use of this class and {@link PassageConceptCache} is optional.
 *   You may want to use them if you have a predefined list of questions and/or passages, but it
 *   can take much longer time for {@link ConceptProvider}s and {@link SynonymExpansionProvider}s
 *   to process the questions and passages individually than all together.
 *   The cache generated in this processing step can be reused later.
 * </p>
 *
 * @see PassageConceptCache
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/20/15
 */
public class QuestionConceptCache extends JCasAnnotator_ImplBase {

  private List<ConceptProvider> conceptProviders;
  
  private List<SynonymExpansionProvider> synonymExpansionProviders;

  private List<String> texts;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    texts = new ArrayList<>();
    // concept cache
    String conceptProviderNames = UimaContextHelper
            .getConfigParameterStringValue(context, "concept-providers");
    conceptProviders = ProviderCache.getProviders(conceptProviderNames, ConceptProvider.class);
    // synonym cache
    String synonymExpansionProviderNames = UimaContextHelper.getConfigParameterStringValue(context,
            "synonym-expansion-providers");
    synonymExpansionProviders = ProviderCache.getProviders(synonymExpansionProviderNames,
            SynonymExpansionProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Question question = TypeUtil.getQuestion(jcas);
    texts.add(question.getText());
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    ConceptCacheUtil.cacheTexts(texts, conceptProviders, synonymExpansionProviders);
    conceptProviders.forEach(ConceptProvider::destroy);
    synonymExpansionProviders.forEach(SynonymExpansionProvider::destroy);
  }

}
