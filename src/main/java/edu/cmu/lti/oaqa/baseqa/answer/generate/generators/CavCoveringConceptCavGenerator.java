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
import static java.util.stream.Collectors.toSet;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.base.Charsets;
import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * <p>
 *   A {@link CandidateAnswerVariant} generator for <tt>_CHOICE</tt> and <tt>_QUANTITY</tt>
 *   questions, which could be either manually specified in the input question or identified by a
 *   question classifier, e.g.
 *   {@link edu.cmu.lti.oaqa.baseqa.answer_type.AnswerTypeClassifierPredictor}.
 * </p>
 * <p>
 *   This class is intended to find longer spans in the relevant snippets for the same
 *   {@link CandidateAnswerVariant}s that have been identified from the questions only. The same
 *   task for other <tt>FACTOID</tt> and <tt>LIST</tt> questions is done at the same time when the
 *   {@link CandidateAnswerVariant}s are first extracted from the relevant passages directly.
 * </p>
 *
 * @see ConceptCavGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class CavCoveringConceptCavGenerator extends ConfigurableProvider implements CavGenerator {

  private Set<String> stoplist;

  private boolean checkStoplist;

  private boolean filterQuestionTokens;

  private boolean filterQuestionConcepts;

  private static final String CHOICE_TYPE = "_CHOICE";

  private static final String QUANTITY_TYPE = "_QUANTITY";

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String stoplistFile = (String) getParameterValue("stoplist");
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistFile), Charsets.UTF_8).stream()
              .collect(toSet());
      checkStoplist = true;
    } catch (Exception e) {
      checkStoplist = false;
    }
    filterQuestionTokens = (Boolean) getParameterValue("filter-question-tokens");
    filterQuestionConcepts = (Boolean) getParameterValue("filter-question-concepts");
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return (TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST"))
            && TypeUtil.getLexicalAnswerTypes(jcas).stream().limit(1)
                    .map(LexicalAnswerType::getLabel)
                    .anyMatch(label -> label.equals(CHOICE_TYPE) || label.equals(QUANTITY_TYPE));
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    // create answer variants groups from each concept, and make sure each covers at each one
    // existing CAV
    List<CandidateAnswerVariant> cavs = TypeUtil.getConcepts(jcas).stream()
            .filter(concept -> TypeUtil.getConceptMentions(concept).stream()
                    .anyMatch(cmention -> !JCasUtil
                            .selectCovered(CandidateAnswerOccurrence.class, cmention).isEmpty()))
            .map(concept -> CavUtil.createCandidateAnswerVariant(jcas, concept)).collect(toList());
    // filter out stopwords and concepts in the question
    Set<String> filteredStrings = new HashSet<>();
    if (checkStoplist) {
      filteredStrings.addAll(stoplist);
    }
    if (filterQuestionTokens) {
      TypeUtil.getOrderedTokens(jcas).stream()
              .flatMap(token -> Stream.of(token.getCoveredText(), token.getLemmaForm()))
              .map(String::toLowerCase).forEach(filteredStrings::add);
    }
    if (filterQuestionConcepts) {
      TypeUtil.getConceptMentions(jcas).stream().map(ConceptMention::getCoveredText)
              .map(String::toLowerCase).map(String::trim).forEach(filteredStrings::add);
    }
    return CavUtil.cleanup(jcas, cavs, filteredStrings);
  }

}
