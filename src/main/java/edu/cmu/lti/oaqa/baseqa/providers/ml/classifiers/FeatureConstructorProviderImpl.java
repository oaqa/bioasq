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

package edu.cmu.lti.oaqa.baseqa.providers.ml.classifiers;

import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;

/**
 * An implementation of the interface {@link FeatureConstructorProvider} that creates features
 * mostly for the purpose of
 * {@link edu.cmu.lti.oaqa.baseqa.answer_type.AnswerTypeClassifierPredictor}.
 *
 * TODO: To be migrated to {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer}
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/15
 */
public class FeatureConstructorProviderImpl extends ConfigurableProvider
        implements FeatureConstructorProvider {

  private List<List<String>> quantityQuestionPhrases;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String quantityQuestionWordsPath = (String) getParameterValue("quantity-question-words-path");
    try {
      quantityQuestionPhrases = Resources
              .readLines(getClass().getResource(quantityQuestionWordsPath), UTF_8).stream()
              .map(String::trim).map(line -> Arrays.asList(line.split(" "))).collect(toList());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public Map<String, Double> constructFeatures(JCas jcas) {
    Map<String, Double> features = new HashMap<>();
    // question type
    features.put("question-type:" + TypeUtil.getQuestion(jcas).getQuestionType(), 1.0);
    // cmention
    List<ConceptMention> cmentions = TypeUtil.getOrderedConceptMentions(jcas);
    for (ConceptMention cmention : cmentions) {
      double score = cmention.getScore();
      if (Double.isNaN(score))
        score = 1.0;
      for (ConceptType st : TypeUtil.getConceptTypes(cmention.getConcept())) {
        String semTypeAbbr = st.getAbbreviation();
        String semType = "concept-type:" + semTypeAbbr;
        features.put(semType, score);
        String semTypePrefix = "concept-type-prefix:" + semTypeAbbr.split(":", 2)[0];
        features.put(semTypePrefix, score);
        if (!features.containsKey(semType) || features.get(semType) < score) {
          features.put(semType, score);
        }
        Token token = TypeUtil.getHeadTokenOfAnnotation(cmention);
        String semTypeDepLabel =
                "concept-type:" + semTypeAbbr + "/dependency-label:" + token.getDepLabel();
        if (!features.containsKey(semTypeDepLabel) || features.get(semTypeDepLabel) < score) {
          features.put(semTypeDepLabel, score);
        }
        String semTypeHeadDepLabel = "concept-type:" + semTypeAbbr + "/head-dependency-label:" +
                (token.getHead() == null ? "null" : token.getHead().getDepLabel());
        features.put(semTypeHeadDepLabel, score);
      }
    }
    // token
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    for (Token token : tokens) {
      features.put("lemma:" + token.getLemmaForm(), 1.0);
    }
    features.put("first-lemma:" + tokens.get(0).getLemmaForm(), 1.0);
    features.put("last-lemma:" + tokens.get(tokens.size() - 1).getLemmaForm(), 1.0);
    // focus
    Focus focus = TypeUtil.getFocus(jcas);
    if (focus != null) {
      features.put("focus:" + focus.getLabel(), 1.0);
    }
    List<String> lemmas = tokens.stream().map(Token::getLemmaForm).collect(toList());
    boolean choice = (lemmas.get(0).equals("do") || lemmas.get(0).equals("be"))
            && lemmas.contains("or");
    features.put("choice", choice ? 1d : 0d);
    boolean quantity = quantityQuestionPhrases.stream()
            .map(phrase -> Collections.indexOfSubList(lemmas, phrase)).filter(index -> index >= 0)
            .findAny().isPresent();
    features.put("quantity", quantity ? 1.0 : 0.0);
    return features;
  }

}
