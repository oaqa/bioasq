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

package edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers;

import com.google.common.base.CharMatcher;
import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link ConceptSearchResult}s that evaluates whether
 * the same concept names occur in the retrieved candidate {@link ConceptSearchResult}s and the
 * original input question.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class MatchingNameConceptScorer extends AbstractScorer<ConceptSearchResult> {

  private static CharMatcher LD = CharMatcher.JAVA_LETTER_OR_DIGIT;

  private Set<String> normalizedConceptNames;

  private Set<String> normalizedCmentionNames;

  private static String getNormalizedName(String name) {
    return LD.retainFrom(name).toLowerCase();
  }

  private static Set<String> getNormalizedNames(Concept concept) {
    return TypeUtil.getConceptNames(concept).stream()
            .map(MatchingNameConceptScorer::getNormalizedName).collect(toSet());
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    normalizedConceptNames = TypeUtil.getConcepts(jcas).stream()
            .map(MatchingNameConceptScorer::getNormalizedNames).flatMap(Collection::stream)
            .collect(toSet());
    normalizedCmentionNames = TypeUtil.getConceptMentions(jcas).stream()
            .map(cmention -> new String[] { cmention.getMatchedName(), cmention.getCoveredText() })
            .flatMap(Arrays::stream).map(MatchingNameConceptScorer::getNormalizedName)
            .collect(toSet());
  }

  @Override
  public Map<String, Double> score(JCas jcas, ConceptSearchResult result) {
    boolean conceptNameMatch = getNormalizedNames(result.getConcept()).stream()
            .anyMatch(normalizedConceptNames::contains);
    boolean cmentionNameMatch = getNormalizedNames(result.getConcept()).stream()
            .anyMatch(normalizedCmentionNames::contains);
    return ImmutableMap.<String, Double>builder()
            .put("concept-name-match", conceptNameMatch ? 1.0 : 0.0)
            .put("cmention-name-match", cmentionNameMatch ? 1.0 : 0.0).build();
  }

}
