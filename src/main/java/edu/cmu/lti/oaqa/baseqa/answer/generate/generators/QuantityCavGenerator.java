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

import java.util.List;
import java.util.Map;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.baseqa.answer.CavUtil;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * This class extracts the quantity words from the relevant passages (i.e. <tt>CD</tt> POS) and use
 * them as the {@link CandidateAnswerVariant}s for <tt>_QUANTITY</tt> questions. The units (e.g. cm)
 * may not be included in the extracted {@link CandidateAnswerVariant}, and thus
 * {@link CavCoveringConceptCavGenerator} can be used to expand the span of the answer texts.
 *
 * @see CavCoveringConceptCavGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class QuantityCavGenerator extends ConfigurableProvider implements CavGenerator {

  private static final String QUANTITY_TYPE = "_QUANTITY";

  private static final String CD_POS_TAG = "CD";

  private String viewNamePrefix;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    viewNamePrefix = (String) getParameterValue("view-name-prefix");
    return ret;
  }

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return (TypeUtil.getQuestion(jcas).getQuestionType().equals("FACTOID")
            || TypeUtil.getQuestion(jcas).getQuestionType().equals("LIST"))
            && TypeUtil.getLexicalAnswerTypes(jcas).stream().limit(1)
                    .map(LexicalAnswerType::getLabel).allMatch(QUANTITY_TYPE::equals);
  }

  @Override
  public List<CandidateAnswerVariant> generate(JCas jcas) throws AnalysisEngineProcessException {
    return ViewType.listViews(jcas, viewNamePrefix).stream()
            .flatMap(view -> TypeUtil.getOrderedTokens(view).stream()
                    .filter(token -> CD_POS_TAG.equals(token.getPartOfSpeech()))
                    .map(token -> CavUtil.createCandidateAnswerVariant(jcas, token)))
            .collect(toList());
  }

}
