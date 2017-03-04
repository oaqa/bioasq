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

package edu.cmu.lti.oaqa.baseqa.question.parse;

import com.google.common.base.CharMatcher;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import edu.emory.clir.clearnlp.component.mode.morph.EnglishMPAnalyzer;
import edu.emory.clir.clearnlp.dependency.DEPFeat;
import edu.emory.clir.clearnlp.dependency.DEPNode;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

import static java.lang.Character.isUpperCase;
import static java.lang.Character.toUpperCase;

/**
 * This {@link JCasAnnotator_ImplBase} as a post-processing step after {@link QuestionParser}
 * updates the lemmas for better downstream processing, including de-downcasing the proper nouns
 * and de-normalizing cardinals.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/4/14
 */
public class QuestionLemmaDedowncaserDenormalizer extends JCasAnnotator_ImplBase {

  private EnglishMPAnalyzer mpAnalyzer;

  private static final Logger LOG = LoggerFactory
          .getLogger(QuestionLemmaDedowncaserDenormalizer.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    mpAnalyzer = new EnglishMPAnalyzer();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    // use original ClearNLP lemmatizer in case missing
    tokens.stream().filter(token -> token.getLemmaForm() == null).forEach(token -> {
      DEPNode node = createNode(token);
      mpAnalyzer.analyze(node);
      token.setLemmaForm(node.getLemma());
    } );
    // try to de-downcase for proper nouns
    tokens.stream().filter(token -> equalsPosTag("NNP", token))
            .forEach(QuestionLemmaDedowncaserDenormalizer::setLemmaByText);
    tokens.stream().filter(token -> equalsPosTag("NNPS", token)).forEach(token -> {
      char[] tokenText = token.getCoveredText().toCharArray();
      char[] lemma = token.getLemmaForm().toCharArray();
      for (int i = 0; i < lemma.length; i++) {
        if (isUpperCase(tokenText[i]))
          lemma[i] = toUpperCase(lemma[i]);
      }
      token.setLemmaForm(new String(lemma));
    } );
    // de-normalization
    tokens.stream().filter(token -> equalsPosTag("CD", token))
            .forEach(QuestionLemmaDedowncaserDenormalizer::setLemmaByText);
    tokens.stream().filter(token -> CharMatcher.JAVA_DIGIT.matchesAnyOf(token.getCoveredText()))
            .forEach(QuestionLemmaDedowncaserDenormalizer::setLemmaByText);

    if (LOG.isTraceEnabled()) {
      tokens.forEach(token -> LOG.trace("{} {} {}", token.getCoveredText(), token.getLemmaForm(),
              token.getPartOfSpeech()));
    }
  }

  private static boolean equalsPosTag(String posTag, Token token) {
    return posTag.equalsIgnoreCase(token.getPartOfSpeech());
  }

  private static void setLemmaByText(Token token) {
    token.setLemmaForm(token.getCoveredText());
  }

  private static DEPNode createNode(Token token) {
    return new DEPNode(-1, token.getCoveredText(), null, token.getPartOfSpeech(), new DEPFeat());
  }

}
