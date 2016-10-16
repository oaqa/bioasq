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

package edu.cmu.lti.oaqa.baseqa.answer.modify.modifiers;

import static java.util.stream.Collectors.toList;

import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * This class prunes {@link Answer}s of low scores by a fixed absolute <tt>threshold</tt> and/or
 * a relative <tt>ratio</tt> (to the maximum score among all the candidate answers for the same
 * question). This class is used for <tt>LIST</tt> questions.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/1/15
 */
public class ListAnswerPruner extends ConfigurableProvider implements AnswerModifier {

  private double threshold = Double.NEGATIVE_INFINITY;

  private double ratio = Double.NEGATIVE_INFINITY;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    if (null != getParameterValue("threshold")) {
      threshold = Double.class.cast(getParameterValue("threshold"));
    }
    if (null != getParameterValue("ratio")) {
      ratio = Double.class.cast(getParameterValue("ratio"));
    }
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST");
  }

  @Override
  public void modify(JCas jcas) throws AnalysisEngineProcessException {
    if (threshold != Double.NEGATIVE_INFINITY) {
      List<Answer> removedAnswers = TypeUtil.getRankedAnswers(jcas).stream()
              .filter(answer -> answer.getScore() < threshold).collect(toList());
      removedAnswers.forEach(Answer::removeFromIndexes);
    }
    if (ratio != Double.NEGATIVE_INFINITY) {
      List<Answer> answers = TypeUtil.getRankedAnswers(jcas);
      double cutoff = answers.get(0).getScore() * ratio;
      List<Answer> removedAnswers = answers.stream().filter(answer -> answer.getScore() < cutoff)
              .collect(toList());
      removedAnswers.forEach(Answer::removeFromIndexes);
    }
  }

}
