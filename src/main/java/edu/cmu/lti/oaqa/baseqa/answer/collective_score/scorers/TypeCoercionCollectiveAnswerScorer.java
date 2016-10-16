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

package edu.cmu.lti.oaqa.baseqa.answer.collective_score.scorers;

import com.google.common.collect.*;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * A collective answer scorer based on whether the top answers share the same concept types.
 * Intuitively, a list question, although may have more than one correct answer, tends to ask about
 * a single type, so the top answers should have the same answer type.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/15/15
 */
public class TypeCoercionCollectiveAnswerScorer extends AbstractScorer<Answer> {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> typecors;

  private Table<Answer, Answer, Double> ntypecors;

  private Table<Answer, Answer, Set<String>> types;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    topLimits = (Iterable<Integer>) getParameterValue("top-limit");
    return ret;
  }

  @Override
  public void prepare(JCas jcas) {
    answers = TypeUtil.getRankedAnswers(jcas);
    // create offset to concept types index
    SetMultimap<String, String> offset2ctypes = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      for (ConceptMention cmention : TypeUtil.getConceptMentions(concept)) {
        Set<String> ctypes = TypeUtil.getConceptTypes(cmention.getConcept()).stream()
                .map(ConceptType::getAbbreviation).collect(toSet());
        offset2ctypes.putAll(TypeUtil.annotationOffset(cmention), ctypes);
      }
    }
    // create answer to concepty types index
    SetMultimap<Answer, String> answer2ctypes = HashMultimap.create();
    for (Answer answer : answers) {
      for (CandidateAnswerVariant cav : TypeUtil.getCandidateAnswerVariants(answer)) {
        for (CandidateAnswerOccurrence cao : TypeUtil.getCandidateAnswerOccurrences(cav)) {
          Set<String> ctypes = offset2ctypes.get(TypeUtil.annotationOffset(cao));
          answer2ctypes.putAll(answer, ctypes);
        }
      }
    }
    // build pariwise similarity matrices
    typecors = HashBasedTable.create();
    ntypecors = HashBasedTable.create();
    types = HashBasedTable.create();
    ImmutableSet<Answer> answerSet = ImmutableSet.copyOf(answers);
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer a1 = pair.get(0);
      Answer a2 = pair.get(1);
      if (a1.equals(a2)) continue;
      Set<String> overlapTypes = Sets.intersection(answer2ctypes.get(a1), answer2ctypes.get(a2));
      if (overlapTypes.size() > 0) {
        typecors.put(a1, a2, (double) overlapTypes.size());
        ntypecors.put(a1, a2, (double) overlapTypes.size() / answer2ctypes.get(a1).size());
        types.put(a1, a2, overlapTypes);
      }
    }
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Map<Answer, Double> neighbor2typecor = typecors.row(answer);
    Map<Answer, Double> neighbor2ntypecor = ntypecors.row(answer);
    Map<Answer, Set<String>> neighbor2types = types.row(answer);
    Map<String, Double> features = new HashMap<>();
    for (int topLimit : topLimits) {
      double avgTypecor = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2typecor.getOrDefault(neighbor, 0.0)).average()
              .orElse(0);
      features.put("typecor-" + topLimit, avgTypecor);
      double avgNTypecor = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2ntypecor.getOrDefault(neighbor, 0.0)).average()
              .orElse(0);
      features.put("ntypecor-" + topLimit, avgNTypecor);
      answers.subList(0, Math.min(answers.size(), topLimit)).stream().map(neighbor2types::get)
              .filter(Objects::nonNull).flatMap(Set::stream)
              .forEach(type -> features.put("type-" + topLimit + "@" + type, 1.0));
    }
    return features;
  }

}
