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

package edu.cmu.lti.oaqa.baseqa.question.focus;

import java.util.List;
import java.util.Set;

import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableSet;

import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * <p>
 *   This class implements a simple rule based method to extract the lexical answer type, which
 *   identifieds the first noun that is also a direct child of the root of the question and has a
 *   "dep" dependency relation.
 * </p>
 * <p>
 *   A preliminary experiment shows this method can correctly identify the focused word at the
 *   accuracy of more than 80%.
 * </p>
 *
 * TODO: The name might need to be updated. Focus or lexical answer type?
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class QuestionFocusExtractor extends JCasAnnotator_ImplBase {

  private static final String ROOT_DEP_LABEL = "root";

  private static final String DEP_DEP_LABEL = "dep";

  private static final Set<String> NOUN_POS_TAGS = ImmutableSet.of("NN", "NNP", "NNS", "NNPS");

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    Token root = tokens.stream()
            .filter(token -> token.getHead() == null || ROOT_DEP_LABEL.equals(token.getDepLabel()))
            .findFirst().orElseThrow(AnalysisEngineProcessException::new);
    tokens.stream().filter(token -> token.getHead() != null)
            .filter(token -> token.getHead().equals(root))
            .filter(token -> NOUN_POS_TAGS.contains(token.getPartOfSpeech()))
            .filter(token -> !DEP_DEP_LABEL.equals(token.getDepLabel())).findFirst()
            .ifPresent(token -> {
              System.out.println("Found focus: " + token.getLemmaForm());
              TypeFactory.createFocus(jcas, token, token.getLemmaForm()).addToIndexes();
            } );
  }

}
