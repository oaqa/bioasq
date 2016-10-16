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

import com.google.common.base.Charsets;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that counts the stop words in each
 * {@link CandidateAnswerOccurrence}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/21/15
 */
public class StopwordCountAnswerScorer extends AbstractScorer<Answer> {

  private Set<String> stoplist;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    double[] stopwordRatios = caos.stream().map(cao -> JCasUtil.selectCovered(Token.class, cao))
            .mapToDouble(tokens -> Scorer.safeDividedBy(tokens.stream()
                    .filter(token -> stoplist.contains(token.getCoveredText().toLowerCase()) ||
                            stoplist.contains(token.getLemmaForm())).count(), tokens.size()))
            .toArray();
    return Scorer.generateSummaryFeatures(stopwordRatios, "stopword", "avg", "min", "one-ratio",
            "any-one");
  }

}
