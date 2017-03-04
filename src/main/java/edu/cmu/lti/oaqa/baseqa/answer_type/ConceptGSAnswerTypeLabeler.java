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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * A {@link GSAnswerTypeLabeler} that uses a {@link ConceptProvider} to identify all the
 * {@link Concept}s with their {@link ConceptType}s from a paragraph.
 * All the answers to the same questions (including answer variants and list of answers to
 * <tt>LIST</tt> questions) are concatenated using a comma.
 * In contrast, {@link ConceptSearchProvider} uses a
 * {@link edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider} to directly look up a single
 * {@link Concept} uses its name.
 *
 * @see ConceptProvider
 * @see ConceptSearchGSAnswerTypeLabeler
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/17/16
 */
public class ConceptGSAnswerTypeLabeler extends GSAnswerTypeLabeler {

  private ConceptProvider conceptProvider;

  private static final Logger LOG = LoggerFactory.getLogger(ConceptGSAnswerTypeLabeler.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
  }

  @Override
  protected void annotateConceptTypesForGSAnswers(List<QuestionAnswerTypes> qats)
          throws AnalysisEngineProcessException {
    long answerCount = qats.stream().map(QuestionAnswerTypes::getAnswers).mapToLong(Set::size)
            .sum();
    LOG.info("Fetch labels for {} questions ({} answers).", qats.size(), answerCount);
    // general text requests
    List<String> texts = qats.stream().map(qat -> String.join(", ", qat.getAnswers()))
            .collect(toList());
    // fetch concepts
    List<Concept> concepts = conceptProvider.getConcepts(texts, "__GS_ANSWER__");
    SetMultimap<String, String> cmention2types = HashMultimap.create();
    for (Concept concept : concepts) {
      Set<String> types = TypeUtil.getConceptTypes(concept).stream().map(ConceptType::getName)
              .collect(toSet());
      Set<String> cmentions = TypeUtil.getConceptMentions(concept).stream()
              .map(ConceptMention::getCoveredText).collect(Collectors.toSet());
      cmentions.forEach(cmention -> cmention2types.putAll(cmention, types));
    }
    // map the concept types back to qats
    for (QuestionAnswerTypes qat : qats) {
      qat.getAnswers().stream().filter(cmention2types::containsKey)
              .forEach(answer -> qat.addAnswerTypes(answer, cmention2types.get(answer)));
    }
  }

}
