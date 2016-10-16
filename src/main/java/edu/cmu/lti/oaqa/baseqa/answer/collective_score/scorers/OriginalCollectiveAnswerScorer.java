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

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toMap;

/**
 * A collective answer scorer that copies the original answer score from the individual answer
 * scoring process.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/15/15
 */
public class OriginalCollectiveAnswerScorer extends AbstractScorer<Answer> {

  private Map<Answer, Double> answer2irank;

  @Override
  public void prepare(JCas jcas) {
    List<Answer> answers = TypeUtil.getRankedAnswers(jcas);
    answer2irank = IntStream.range(0, answers.size()).boxed()
            .collect(toMap(answers::get, i -> 1.0 / (1.0 + i)));
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    return ImmutableMap.<String, Double> builder().put("orig-score", answer.getScore())
            .put("orig-rank", answer2irank.getOrDefault(answer, 0.0)).build();
  }

}
