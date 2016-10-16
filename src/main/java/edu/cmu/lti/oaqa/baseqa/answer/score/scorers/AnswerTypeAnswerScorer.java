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

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that uses nominal raw
 * {@link edu.cmu.lti.oaqa.type.answer.AnswerType} texts as features, and generates a score of 1.0
 * for this particular feature, and 0.0 otherwise.
 *
 * @see TypeCoercionAnswerScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class AnswerTypeAnswerScorer extends AbstractScorer<Answer> {

  private Map<String, Double> feat2value;

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    Collection<LexicalAnswerType> answerTypes = TypeUtil.getLexicalAnswerTypes(jcas);
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    for (LexicalAnswerType answerType : answerTypes) {
      builder.put("at-" + answerType.getLabel(), 1.0);
    }
    Question question = TypeUtil.getQuestion(jcas);
    builder.put("qt-" + question.getQuestionType(), 1.0);
    feat2value = builder.build();
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    return feat2value;
  }

}
