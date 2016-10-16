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
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that uses nominal raw
 * {@link ConceptType} names of all covering {@link Concept}s as features, and generates a score of
 * 1.0 for this particular feature, and 0.0 otherwise.
 *
 * @see ConceptOverlappingCountAnswerScorer
 * @see ConceptProximityAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class ConceptTypeAnswerScorer extends AbstractScorer<Answer> {

  private SetMultimap<String, String> offset2ctypes;

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    offset2ctypes = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      Set<String> ctypes = TypeUtil.getConceptTypes(concept).stream()
              .map(ConceptType::getAbbreviation).collect(toSet());
      for (ConceptMention cmention : TypeUtil.getConceptMentions(concept))
        offset2ctypes.putAll(TypeUtil.annotationOffset(cmention), ctypes);
    }
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    Multiset<String> ctypes = HashMultiset.create();
    caos.stream().map(TypeUtil::annotationOffset).map(offset2ctypes::get).forEach(ctypes::addAll);
    ImmutableMap.Builder<String, Double> feat2value = ImmutableMap.builder();
    for (Multiset.Entry<String> entry : ctypes.entrySet()) {
      String type = "ctype-" + entry.getElement();
      feat2value.put(type + "/ratio", Scorer.safeDividedBy(entry.getCount(), ctypes.size()));
      feat2value.put(type + "/binary", 1.0);
    }
    return feat2value.build();
  }

}
