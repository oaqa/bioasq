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

import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.*;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.SetMultimap;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * This class tries to expand the answer spans of extracted {@link CandidateAnswerVariant}s by
 * first identifying their head words in their parse trees, and including all the leaf nodes of the
 * subtrees.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class CoveringPhraseCavGenerator extends ConfigurableProvider implements CavGenerator {

  // private static final String PUNCT_DEP_LABEL = "punct";

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST");
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    Set<Token> heads = TypeUtil.getCandidateAnswerVariants(jcas).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
            .map(TypeUtil::getHeadTokenOfAnnotation).filter(Objects::nonNull).collect(toSet());
    Set<Token> parents = heads.stream().map(Token::getHead).filter(Objects::nonNull)
            .filter(t -> !heads.contains(t)).collect(toSet());
    Map<JCas, List<Token>> view2parents = parents.stream().collect(groupingBy(CavUtil::getJCas));
    return view2parents.entrySet().stream().flatMap(entry -> {
      JCas view = entry.getKey();
      List<Token> tokens = TypeUtil.getOrderedTokens(view);
      SetMultimap<Token, Token> head2children = CavUtil.getHeadTokenMap(tokens);
      return entry.getValue().stream()
              .map(parent -> CavUtil.createCandidateAnswerOccurrenceFromDepBranch(view, parent,
                      head2children, null))
              .map(cao -> TypeFactory.createCandidateAnswerVariant(jcas,
                      Collections.singletonList(cao)));
    } ).collect(toList());
  }

}
