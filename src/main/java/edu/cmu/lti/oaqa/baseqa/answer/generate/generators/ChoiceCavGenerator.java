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

package edu.cmu.lti.oaqa.baseqa.answer.generate.generators;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.stream.Stream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.SetMultimap;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * A {@link CandidateAnswerVariant} generator designed for <tt>_CHOICE</tt> questions only. It first
 * identifies the connector token <it>or</it>, then locate the adjacent phrases around <it>or</it>,
 * and use them as {@link CandidateAnswerVariant}s.
 *
 * @see CavCoveringConceptCavGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class ChoiceCavGenerator extends ConfigurableProvider implements CavGenerator {

  private static final String CHOICE_TYPE = "_CHOICE";

  private static final String OR_LEMMA = "or";

  private static final String CONJ_DEP_LABEL = "conj";

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return (TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST"))
            && TypeUtil.getLexicalAnswerTypes(jcas).stream().limit(1)
                    .map(LexicalAnswerType::getLabel).allMatch(CHOICE_TYPE::equals);
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    SetMultimap<Token, Token> head2children = CavUtil.getHeadTokenMap(tokens);
    Token orToken;
    try {
      orToken = tokens.stream().filter(t -> OR_LEMMA.equals(t.getLemmaForm())).findAny().get();
    } catch (NoSuchElementException e) {
      return new ArrayList<>();
    }
    // identify head tokens for choices from the question
    Token mainToken = orToken.getHead();
    List<Token> alternativeTokens = head2children.get(mainToken).stream()
            .filter(t -> CONJ_DEP_LABEL.equals(t.getDepLabel())).collect(toList());
    List<CandidateAnswerVariant> cavs = Stream
            .concat(Stream.of(mainToken), alternativeTokens.stream())
            .map(token -> CavUtil.createCandidateAnswerVariant(jcas, token)).collect(toList());
    // find CAVs from evidence passages
    Stream.concat(Stream.of(mainToken), alternativeTokens.stream())
            .map(token -> JCasUtil.selectCovering(ConceptMention.class, token))
            .flatMap(Collection::stream).map(ConceptMention::getConcept)
            .map(concept -> CavUtil.createCandidateAnswerVariant(jcas, concept)).forEach(cavs::add);
    return cavs;
  }

}
