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

package edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers;

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.ecd.BaseExperimentBuilder;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngine;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.util.CasCopier;

import java.util.*;

import static java.util.stream.Collectors.toSet;

/**
 * <p>
 *   A <tt>YESNO</tt> answer evidencer based on the idea of question inversion by first converting
 *   the <tt>YESNO</tt> question to a factoid question, then applies an existing factoid question
 *   answering pipeline to generate a list of alternate candidate answers, and finally evidence
 *   each candidate answer and rank them.
 *   If the expected answer in the original question is also ranked at the top among all candidates
 *   for the factoid question, then the statement is true.
 * </p>
 * <p>
 *   First, an expected answer is extracted from the question (here, the last {@link ConceptMention}
 *   is used), and the corresponding concept type is used as the expected
 *   {@link edu.cmu.lti.oaqa.type.answer.AnswerType}.
 *   Then, the pipeline specified in the <tt>factoid-pipeline</tt> is used to generate alternate
 *   answers, and the rank and score of the expected answer in the predicted answer list are used
 *   as features.
 * </p>
 *
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class AlternateAnswerYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  private AnalysisEngine[] aes;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    String pipeline = String.class.cast(getParameterValue("factoid-pipeline"));
    System.out.println(pipeline);
    aes = BaseExperimentBuilder.createAnnotators(pipeline);
    return true;
  }

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    // get the expected answer
    ConceptMention lastCmention = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .min(Comparator.comparingInt(ConceptMention::getEnd).reversed()
                    .thenComparingInt(ConceptMention::getBegin)).orElse(null);
    if (lastCmention == null) return ImmutableMap.of();
    int begin = lastCmention.getBegin();
    int end = lastCmention.getEnd();
    // add the concept types of the expected answer as answer types
    Set<String> expectedAnswers = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .filter(cmention -> cmention.getBegin() == begin && cmention.getEnd() == end)
            .map(ConceptMention::getConcept).map(TypeUtil::getConceptNames)
            .flatMap(Collection::stream).collect(toSet());

    // clone current jcas to a tempJcas
    JCas tempJcas;
    try {
      tempJcas = JCasFactory.createJCas();
    } catch (UIMAException e) {
      throw new AnalysisEngineProcessException(e);
    }
    CasCopier.copyCas(jcas.getCas(), tempJcas.getCas(), true);
    // execute factoid QA pipeline to generate CAVs
    for (AnalysisEngine ae : aes) {
      ae.process(tempJcas);
    }
    // get answers
    List<Answer> alternateAnswers = TypeUtil.getRankedAnswers(tempJcas);
    double alternateAnswerCount = alternateAnswers.size();
    double maxScore = alternateAnswers.stream().mapToDouble(Answer::getScore).max().orElse(1.0);

    // compare the alternate answers with expected answers
    ImmutableMap.Builder<String, Double> features = ImmutableMap.builder();
    for (int rank = 0; rank < alternateAnswers.size(); rank++) {
      Answer alternateAnswer = alternateAnswers.get(rank);
      if (TypeUtil.getCandidateAnswerVariantNames(alternateAnswer).stream()
              .anyMatch(expectedAnswers::contains)) {
        double score = alternateAnswer.getScore();
        int rankBin = rank < 10 ? rank : (rank / 10) * 10;
        features.put("expected-answer-rank@" + rankBin, 1.0);
        features.put("expected-answer-rank-reciprocal", 1.0 / (1.0 + rank));
        features.put("expected-answer-rank-ratio", 1.0 - rank / alternateAnswerCount);
        features.put("expected-answer-score", score);
        features.put("expected-answer-score-ratio", score / maxScore);
        System.out.println("Expected answer is ranked at " + rankBin + "/" + alternateAnswerCount +
                " with score " + score + "/" + maxScore);
        break;
      }
    }
    tempJcas.release();
    return features.build();
  }

}
