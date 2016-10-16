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
import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.baseqa.learning_base.Scorer;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toSet;

/**
 * An instance of an {@link AbstractScorer} for {@link Answer}s that uses the parse tree of each
 * {@link CandidateAnswerOccurrence} to create features, including the depth of the parse tree,
 * whether it is a constituent forest, etc.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class ParseAnswerScorer extends AbstractScorer<Answer> {

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Set<CandidateAnswerOccurrence> caos = TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .collect(toSet());
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    double[] depths = caos.stream().map(TypeUtil::getHeadTokenOfAnnotation)
            .mapToDouble(CavUtil::getDepth).toArray();
    builder.putAll(Scorer.generateSummaryFeatures(depths, "depth", "avg", "max", "min"));
    double[] constituentForests = caos.stream().map(cao -> CavUtil
            .isConstituentForest(CavUtil.getJCas(cao), JCasUtil.selectCovered(Token.class, cao)))
            .mapToDouble(value -> value ? 1.0 : 0.0).toArray();
    builder.putAll(Scorer.generateSummaryFeatures(constituentForests, "constituent-forest",
            "avg", "max", "min", "one-ratio", "any-one"));
    double[] constituents = caos.stream().map(cao -> CavUtil
            .isConstituent(CavUtil.getJCas(cao), JCasUtil.selectCovered(Token.class, cao)))
            .mapToDouble(value -> value ? 1.0 : 0.0).toArray();
    builder.putAll(Scorer.generateSummaryFeatures(constituents, "constituent", "avg", "max",
            "min", "one-ratio", "any-one"));
    return builder.build();
  }

}
