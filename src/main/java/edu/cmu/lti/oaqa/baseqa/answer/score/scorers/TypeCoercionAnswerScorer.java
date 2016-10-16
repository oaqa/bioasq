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

package edu.cmu.lti.oaqa.baseqa.answer.score.scorers;

import com.google.common.collect.*;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that counts the number of predicted
 * (top-<it>k</it>) {@link edu.cmu.lti.oaqa.type.answer.AnswerType}s that are the {@link Answer}'s
 * concept type. Note: the current "coercion" is simply "exact match".
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/17/15
 */
public class TypeCoercionAnswerScorer extends AbstractScorer<Answer> {

  private List<Integer> atLimits;

  private SetMultimap<String, String> offset2ctypes;

  private List<String> ats;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    atLimits = Lists.newArrayList((Iterable<Integer>) getParameterValue("at-limits"));
    return ret;
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    offset2ctypes = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      Set<String> ctypes = TypeUtil.getConceptTypes(concept).stream()
              .map(ConceptType::getAbbreviation).collect(toSet());
      for (ConceptMention cmention : TypeUtil.getConceptMentions(concept))
        offset2ctypes.putAll(TypeUtil.annotationOffset(cmention), ctypes);
    }
    ats = TypeUtil.getLexicalAnswerTypes(jcas).stream().map(LexicalAnswerType::getLabel)
            .collect(toList());
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    List<Set<String>> typesList = caos.stream()
            .map(cao -> offset2ctypes.get(TypeUtil.annotationOffset(cao))).collect(toList());
    ImmutableMap.Builder<String, Double> feat2value = ImmutableMap.builder();
    atLimits.stream().filter(limit -> limit <= ats.size()).forEach(limit -> {
      Set<String> limitedLats = ImmutableSet.copyOf(ats.subList(0, limit));
      double[] typecorRatios = typesList.stream().mapToDouble(types -> {
        int overlap = Sets.intersection(limitedLats, types).size();
        int maxOverlap = Math.min(limitedLats.size(), types.size());
        return Scorer.safeDividedBy(overlap, maxOverlap);
      } ).toArray();
      feat2value.putAll(Scorer.generateSummaryFeatures(typecorRatios, "type-coercion-" + limit,
              "avg", "max", "min", "pos-ratio", "one-ratio", "any-one"));
    } );
    return feat2value.build();
  }

}
