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

import com.google.common.collect.Sets;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * This {@link JCasAnnotator_ImplBase} as a post-processing step after {@link QuestionParser}
 * changes noun tags to proper noun tags ("NNPS" and "NNP") accroding to a regex given in the
 * parameter <tt>regex</tt>.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/4/14
 */
public class QuestionRegExProperNounTagger extends JCasAnnotator_ImplBase {

  private boolean requireNoun;

  private Pattern pattern;

  private Pattern patternFirst;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    requireNoun = UimaContextHelper.getConfigParameterBooleanValue(context, "require-noun", true);
    String regex = UimaContextHelper.getConfigParameterStringValue(context, "regex");
    String regexFirst = UimaContextHelper.getConfigParameterStringValue(context, "regex-first",
            regex);
    pattern = Pattern.compile(regex);
    patternFirst = Pattern.compile(regexFirst);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    Token firstToken = tokens.get(0);
    if ((!requireNoun || isNoun(firstToken)) && matchesTokenText(patternFirst, firstToken)) {
      updatePosTagToProperNoun(firstToken);
    }
    tokens.stream()
            .filter(token -> (!requireNoun || isNoun(token)) && matchesTokenText(pattern, token))
            .forEach(token -> updatePosTagToProperNoun(token));
  }

  private static boolean matchesTokenText(Pattern pattern, Token token) {
    return pattern.matcher(token.getCoveredText()).matches();
  }

  private static Set<String> nouns = Sets.newHashSet("nn", "nnp", "nns", "nnps");

  private static boolean isNoun(Token token) {
    return nouns.contains(token.getPartOfSpeech().toLowerCase());
  }

  private static void updatePosTagToProperNoun(Token token) {
    token.setPartOfSpeech(token.getPartOfSpeech().toLowerCase().endsWith("s") ? "NNPS" : "NNP");
  }

}
