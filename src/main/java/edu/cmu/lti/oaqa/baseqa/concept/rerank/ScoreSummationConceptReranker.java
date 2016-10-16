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

package edu.cmu.lti.oaqa.baseqa.concept.rerank;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.Collection;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toMap;

/**
 * This {@link ConceptSearchResult} reranker accumulates the scores of all
 * {@link ConceptSearchResult} that have the same name after text normalization, and assign the sum
 * of the scores to all the {@link ConceptSearchResult}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class ScoreSummationConceptReranker extends JCasAnnotator_ImplBase {

  private int limit;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    limit = UimaContextHelper.getConfigParameterIntValue(context, "limit", 5);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<ConceptSearchResult> concepts = TypeUtil.getRankedConceptSearchResults(jcas);
    concepts.forEach(ConceptSearchResult::removeFromIndexes);
    Map<String, ConceptSearchResult> text2concept = concepts.stream()
            .collect(toMap(concept -> normalizeText(concept.getText()), Function.identity(),
                    (c1, c2) -> {
                      c1.setScore(c1.getScore() + c2.getScore());
                      return c1;
                    }));
    TypeUtil.rankedSearchResultsByScore(text2concept.values(), limit)
            .forEach(ConceptSearchResult::addToIndexes);
  }

  private static String normalizeText(String text) {
    return text.replaceAll("[-+.^:,\"]", "").toLowerCase();
  }

}
