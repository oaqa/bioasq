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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * This {@link JCasAnnotator_ImplBase} assumes an input {@link JCas} contains a collection of
 * identified {@link Concept}s, but some of them have not been associated with a {@link Concept}
 * in a knowledge, i.e. no Concept ID or synonym. The goal of this class is to use a
 * {@link ConceptSearchProvider} to look up the concept name, and a {@link SynonymExpansionProvider}
 * to find the synonyms of the concept.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/19/15
 */
public class ConceptSearcher extends JCasAnnotator_ImplBase {

  private ConceptSearchProvider conceptSearchProvider;

  private SynonymExpansionProvider synonymExpanisonProvider;

  private static final String NAME_NORMALIZATION = " \\(.*?\\)$| \\[.*?\\]$|\\*|\\^";

  private static final Logger LOG = LoggerFactory.getLogger(ConceptSearcher.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String conceptSearchProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "concept-search-provider");
    conceptSearchProvider = ProviderCache.getProvider(conceptSearchProviderName,
            ConceptSearchProvider.class);
    String synonymExpansionProviderName = UimaContextHelper.getConfigParameterStringValue(context,
            "synonym-expansion-provider");
    synonymExpanisonProvider = ProviderCache.getProvider(synonymExpansionProviderName,
            SynonymExpansionProvider.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<Concept> concepts = TypeUtil.getConcepts(jcas);
    Set<Concept> missingIdConcepts = concepts.stream()
            .filter(concept -> TypeUtil.getConceptIds(concept).isEmpty()).collect(toSet());
    // retrieving IDs
    LOG.info("Retrieving IDs for {} concepts.", missingIdConcepts.size());
    for (Concept concept : missingIdConcepts) {
      Optional<Concept> response = conceptSearchProvider.search(jcas,
              TypeUtil.getConceptPreferredName(concept));
      response.ifPresent(c -> TypeUtil.mergeConcept(jcas, concept, c));
    }
    // retrieving synonyms (names)
    LOG.info("Retrieving synonyms for {} concepts.", concepts.size());
    Map<String, Concept> id2concept = new HashMap<>();
    for (Concept concept : concepts) {
      TypeUtil.getConceptIds(concept).stream().filter(synonymExpanisonProvider::accept)
              .forEach(id -> id2concept.put(id, concept));
    }
    Map<String, Set<String>> id2synonyms = synonymExpanisonProvider
            .getSynonyms(id2concept.keySet());
    for (Map.Entry<String, Concept> entry : id2concept.entrySet()) {
      String id = entry.getKey();
      Concept concept = entry.getValue();
      List<String> names = Stream
              .concat(TypeUtil.getConceptNames(concept).stream(), id2synonyms.get(id).stream())
              .filter(Objects::nonNull).map(name -> name.replaceAll(NAME_NORMALIZATION, ""))
              .distinct().collect(toList());
      concept.setNames(FSCollectionFactory.createStringList(jcas, names));
    }
    if (LOG.isDebugEnabled()) {
      concepts.stream().map(TypeUtil::toString).forEachOrdered(c -> LOG.debug(" - {}", c));
    }
  }
  
  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    conceptSearchProvider.destroy();
    synonymExpanisonProvider.destroy();
  }

}
