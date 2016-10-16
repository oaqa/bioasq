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

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that counts the number (and ratio)
 * of the answer's occurrences in the question.
 * In contrast to {@link TokenOverlappingCountAnswerScorer}, only the {@link ConceptMention} counts,
 * which means (1) a synonym of the same question concept is used in the passage is admitted, (2) an
 * overlapping phrase or word that is not a {@link Concept} is ignored.
 *
 * @see TokenOverlappingCountAnswerScorer
 * @see FocusOverlappingCountAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/30/15
 */
public class ConceptOverlappingCountAnswerScorer extends AbstractScorer<Answer> {

  private SetMultimap<String, Concept> offset2concepts;

  private Set<Concept> qconcepts;

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    offset2concepts = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      for (ConceptMention cmention : TypeUtil.getConceptMentions(concept))
        offset2concepts.put(TypeUtil.annotationOffset(cmention), cmention.getConcept());
    }
    qconcepts = TypeUtil.getConceptMentions(jcas).stream().map(ConceptMention::getConcept)
            .collect(toSet());
  }

  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer)
            .stream().map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    double[] qconceptOverlappingRatios = caos.stream().map(TypeUtil::annotationOffset)
            .map(offset2concepts::get).mapToDouble(concepts -> Scorer
                    .safeDividedBy(Sets.intersection(concepts, qconcepts).size(), concepts.size()))
            .toArray();
    return Scorer.generateSummaryFeatures(qconceptOverlappingRatios, "concept-overlap", "avg",
            "pos-ratio", "any-one");
  }

}
