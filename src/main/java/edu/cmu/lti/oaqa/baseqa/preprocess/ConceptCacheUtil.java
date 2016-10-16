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

import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * A utility class for concept caching.
 *
 * TODO: a standardized interface for all caching services.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/20/15
 */
class ConceptCacheUtil {

  static void cacheTexts(List<String> texts, List<ConceptProvider> conceptProviders,
          List<SynonymExpansionProvider> synonymExpansionProviders) throws AnalysisEngineProcessException {
    List<Concept> concepts = new ArrayList<>();
    for (ConceptProvider conceptProvider : conceptProviders) {
      concepts.addAll(conceptProvider.getConcepts(texts, conceptProvider.getClass().getName()));
    }
    for (SynonymExpansionProvider synonymExpansionProvider : synonymExpansionProviders) {
      Set<String> ids = concepts.stream().map(TypeUtil::getConceptIds).flatMap(Collection::stream)
              .filter(synonymExpansionProvider::accept).collect(toSet());
      synonymExpansionProvider.getSynonyms(ids);
    }
  }

}
