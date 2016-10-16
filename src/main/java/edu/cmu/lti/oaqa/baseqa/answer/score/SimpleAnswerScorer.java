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

package edu.cmu.lti.oaqa.baseqa.answer.score;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.*;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * A simple answer scorer that only considers if the type of each candidate answer matches the
 * predicted {@link edu.cmu.lti.oaqa.type.answer.AnswerType}, and calculate the scores using
 * <tt>((# matching types) / (# candidate answer occurrences) + smoothing) * (# candidate answer occurrences)</tt>.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/3/15
 */
public class SimpleAnswerScorer extends JCasAnnotator_ImplBase {

  private float typeCoerSmoothing;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    typeCoerSmoothing = UimaContextHelper.getConfigParameterFloatValue(context,
            "type-coer-smoothing", 0.1f);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Set<String> lats = TypeUtil.getLexicalAnswerTypes(jcas).stream()
            .map(LexicalAnswerType::getLabel).collect(toSet());
    List<Answer> answers = TypeUtil.getCandidateAnswerVariants(jcas).stream().map(cav -> {
      Collection<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerOccurrences(cav);
      long latCoercionCount = caos.stream()
              .filter(cao -> JCasUtil.selectCovered(ConceptMention.class, cao).stream()
                      .map(ConceptMention::getConcept).map(TypeUtil::getConceptTypes)
                      .flatMap(Collection::stream).map(ConceptType::getAbbreviation)
                      .anyMatch(lats::contains))
              .count();
      double score = (latCoercionCount / (double) caos.size() + typeCoerSmoothing) * caos.size();
      System.out.println(TypeUtil.getCandidateAnswerVariantNames(cav) + " " + score);
      return TypeFactory.createAnswer(jcas, score, Collections.singletonList(cav));
    } ).sorted(TypeUtil.ANSWER_SCORE_COMPARATOR).collect(toList());
    answers.forEach(Answer::addToIndexes);
    System.out.println("Ranked top 5 answers " + answers.stream().limit(5)
            .map(TypeUtil::getCandidateAnswerVariantNames).collect(toList()));
  }

}
