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

package edu.cmu.lti.oaqa.baseqa.question.concept;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

/**
 * This class uses a {@link ConceptProvider} to identify and annotate the {@link Concept}s in the
 * question.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/12/15
 */
public class QuestionConceptRecognizer extends JCasAnnotator_ImplBase {

  private ConceptProvider conceptProvider;

  private static final Logger LOG = LoggerFactory.getLogger(QuestionConceptRecognizer.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Concept> concepts = conceptProvider.getConcepts(jcas);
    concepts.forEach(Concept::addToIndexes);
    concepts.stream().map(TypeUtil::getConceptMentions).flatMap(Collection::stream)
            .forEach(ConceptMention::addToIndexes);
    if (LOG.isInfoEnabled()) {
      LOG.info("Identified concepts:");
      concepts.forEach(c -> LOG.info(" - {}", TypeUtil.toString(c)));
    }
  }

}
