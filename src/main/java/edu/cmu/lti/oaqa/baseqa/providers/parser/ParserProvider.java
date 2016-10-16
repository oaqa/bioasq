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

package edu.cmu.lti.oaqa.baseqa.providers.parser;

import edu.cmu.lti.oaqa.type.nlp.Token;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import java.util.List;

/**
 * An implementation of this interface should wrap a parser to {@link #tokenize(JCas)},
 * {@link #tagPartOfSpeech(JCas)}, {@link #lemmatize(JCas)}, and {@link #parseDependency(JCas)} of
 * the document text in a input {@link JCas}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/12/16
 */
public interface ParserProvider extends Resource {

  List<Token> tokenize(JCas jcas);

  void tagPartOfSpeech(JCas jcas, List<Token> tokens);
  
  default List<Token> tagPartOfSpeech(JCas jcas) {
    List<Token> tokens = tokenize(jcas);
    tagPartOfSpeech(jcas, tokens);
    return tokens;
  }

  void lemmatize(JCas jcas, List<Token> tokens);
  
  default List<Token> lemmatize(JCas jcas) {
    List<Token> tokens = tokenize(jcas);
    tagPartOfSpeech(jcas, tokens);
    lemmatize(jcas, tokens);
    return tokens;
  }

  void parseDependency(JCas jcas, List<Token> tokens);

  default List<Token> parseDependency(JCas jcas) {
    List<Token> tokens = tokenize(jcas);
    tagPartOfSpeech(jcas, tokens);
    lemmatize(jcas, tokens);
    parseDependency(jcas, tokens);
    return tokens;
  }

}
