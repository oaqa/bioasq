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

import com.google.common.base.CharMatcher;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractCandidateProvider;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Set;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toSet;

/**
 * An {@link AbstractCandidateProvider} for {@link Answer}, used in answer score training (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer}), answer score prediction (via
 * {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}), and cross-validation
 * prediction loading (via {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}).
 * Names of the variants are concatenated and used as the <tt>uri</tt>.
 *
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor
 * @see edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/14/16
 */
public class AnswerCandidateProvider extends AbstractCandidateProvider<Answer> {

  @Override
  public Collection<Answer> getCandidates(JCas jcas) {
    return TypeUtil.getRankedAnswers(jcas);
  }

  @Override
  public void setScoreRank(Answer candidate, double score, int rank) {
    candidate.setScore(score);
  }

  @Override
  public String getUri(Answer candidate) {
    return TypeUtil.getCandidateAnswerVariantNames(candidate).stream()
            .map(str -> str.replaceAll("\t", "")).sorted().collect(joining(";"));
  }

  @Override
  public Collection<Answer> getGoldStandards(JCas jcas) {
    return TypeUtil.getRankedAnswers(ViewType.getGsView(jcas));
  }

  @Override
  public boolean match(Answer candidate, Collection<Answer> gs) {
    Set<String> gsTexts = gs.stream().map(TypeUtil::getCandidateAnswerVariantNames)
            .flatMap(Collection::stream).map(CharMatcher.JAVA_LETTER_OR_DIGIT::retainFrom)
            .map(String::toLowerCase).collect(toSet());
    return TypeUtil.getCandidateAnswerVariantNames(candidate).stream()
            .map(CharMatcher.JAVA_LETTER_OR_DIGIT::retainFrom).map(String::toLowerCase)
            .anyMatch(gsTexts::contains);
  }

  @Override
  public String toString(Answer candidate) {
    return TypeUtil.getCandidateAnswerVariantNames(candidate).toString();
  }

}
