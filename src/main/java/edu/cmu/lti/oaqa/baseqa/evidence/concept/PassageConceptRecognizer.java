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

package edu.cmu.lti.oaqa.baseqa.evidence.concept;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link PassageConceptRecognizer} uses a {@link ConceptProvider} to annotate the
 * {@link Concept}s in the passages in the views (all of the views to the annotated should have the
 * same <tt>view-name-prefix</tt>).
 *
 * One can use {@link edu.cmu.lti.oaqa.baseqa.evidence.PassageToViewCopier} to copy the passage
 * texts from the {@link edu.cmu.lti.oaqa.type.retrieval.Passage}s to the individual views.
 *
 * @see edu.cmu.lti.oaqa.baseqa.evidence.PassageToViewCopier
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/12/15
 */
public class PassageConceptRecognizer extends JCasAnnotator_ImplBase {

  private ConceptProvider conceptProvider;

  private String viewNamePrefix;

  private Set<String> allowedConceptTypes;

  private boolean checkConceptTypes;

  private static final Logger LOG = LoggerFactory.getLogger(PassageConceptRecognizer.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-provider");
    conceptProvider = ProviderCache.getProvider(conceptProviderName, ConceptProvider.class);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
    String allowedConceptTypesFile = UimaContextHelper.getConfigParameterStringValue(context,
            "allowed-concept-types", null);
    try {
      allowedConceptTypes = Resources
              .readLines(getClass().getResource(allowedConceptTypesFile), Charsets.UTF_8).stream()
              .collect(toSet());
      checkConceptTypes = true;
    } catch (Exception e) {
      checkConceptTypes = false;
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Concept> concepts = conceptProvider.getConcepts(views).stream()
            .filter(concept -> !checkConceptTypes
                    || containsAllowedConceptType(concept, allowedConceptTypes))
            .collect(toList());
    concepts.forEach(Concept::addToIndexes);
    concepts.stream().map(TypeUtil::getConceptMentions).flatMap(Collection::stream)
            .forEach(ConceptMention::addToIndexes);
    if (LOG.isInfoEnabled()) {
      LOG.info("Identified concepts: ");
      concepts.forEach(c -> LOG.info(" - {}: {}", TypeUtil.getConceptNames(c),
              TypeUtil.getConceptTypeNames(c)));
    }
  }

  private static boolean containsAllowedConceptType(Concept concept,
          Set<String> allowedConceptTypeAbbreviations) {
    return TypeUtil.getConceptTypes(concept).stream().map(ConceptType::getAbbreviation)
            .anyMatch(allowedConceptTypeAbbreviations::contains);
  }

}
