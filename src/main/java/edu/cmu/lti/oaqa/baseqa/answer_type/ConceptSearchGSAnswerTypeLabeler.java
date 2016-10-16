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

package edu.cmu.lti.oaqa.baseqa.answer_type;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.List;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * A {@link GSAnswerTypeLabeler} that uses a {@link ConceptSearchProvider} to look up a
 * {@link edu.cmu.lti.oaqa.type.kb.Concept} in the knowledge using the name of the concept, and
 * retrieve {@link ConceptType}s.
 * Only the abbreviation is used.
 * In contrast, {@link ConceptSearchProvider} uses a
 * {@link edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider} to identify all the {@link Concept}s
 * with their {@link ConceptType}s from a paragraph.
 *
 * @see ConceptSearchProvider
 * @see ConceptGSAnswerTypeLabeler
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/17/15
 */
public class ConceptSearchGSAnswerTypeLabeler extends GSAnswerTypeLabeler {

  private ConceptSearchProvider conceptSearchProvider;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptSearchProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-search-provider");
    conceptSearchProvider = ProviderCache.getProvider(conceptSearchProviderName,
            ConceptSearchProvider.class);
  }

  @Override
  protected void annotateConceptTypesForGSAnswers(List<QuestionAnswerTypes> qats)
          throws AnalysisEngineProcessException {
    long answerCount = qats.stream().map(QuestionAnswerTypes::getAnswers).flatMap(Set::stream)
            .count();
    System.out.println(
            "Fetch labels for " + qats.size() + " questions (" + answerCount + " answers).");
    for (QuestionAnswerTypes qat : qats) {
      for (String answer : qat.getAnswers()) {
        conceptSearchProvider.search(answer).ifPresent(concept -> {
          Set<String> types = TypeUtil.getConceptTypes(concept).stream()
                  .map(ConceptType::getAbbreviation).collect(toSet());
          qat.addAnswerTypes(answer, types);
        });
      }
    }
  }

}
