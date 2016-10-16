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

import com.aliasi.hmm.HiddenMarkovModel;
import com.aliasi.hmm.HmmDecoder;
import com.aliasi.tag.Tagging;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.Tokenizer;
import com.aliasi.tokenizer.TokenizerFactory;
import com.google.common.collect.Iterables;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;
import java.util.stream.StreamSupport;

import static java.util.stream.Collectors.toList;

/**
 * A {@link ParserProvider} that wraps <a href="alias-i.com/lingpipe/">LingPipe</a> parser.
 * One should specify the tokenizer and the path to the POS model in the descriptor via parameters
 * <tt>token-factory</tt> and <tt>pos-model</tt>.
 *
 * <p>
 *   NOTE: LingPipe has its own special
 *   <a href="http://alias-i.com/lingpipe/web/licensing.html">license</a>.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/12/16
 */
public class LingPipeParserProvider extends ConfigurableProvider implements ParserProvider {

  private TokenizerFactory tokenizerFactory;

  private HmmDecoder decoder;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // tokenizer
    String tokenFactoryName = String.class.cast(getParameterValue("token-factory"));
    Class<? extends TokenizerFactory> tokenFactoryClass;
    try {
      tokenFactoryClass = Class.forName(tokenFactoryName).asSubclass(TokenizerFactory.class);
    } catch (ClassNotFoundException e) {
      tokenFactoryClass = IndoEuropeanTokenizerFactory.class;
    }
    @SuppressWarnings("unchecked")
    Object[] tokenFactoryParams = Iterables
            .toArray(Iterable.class.cast(getParameterValue("token-factory-params")), Object.class);
    Class<?>[] tokenFactoryParamsTypes = Arrays.stream(tokenFactoryParams).map(p -> String.class)
            .toArray(Class[]::new);
    try {
      tokenizerFactory = tokenFactoryClass.getConstructor(tokenFactoryParamsTypes)
              .newInstance(tokenFactoryParams);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
            | InvocationTargetException | NoSuchMethodException | SecurityException e) {
      throw new ResourceInitializationException(e);
    }
    // pos
    String posModel = String.class.cast(getParameterValue("pos-model"));
    try (ObjectInputStream ois = new ObjectInputStream(
            this.getClass().getResourceAsStream(posModel))) {
      HiddenMarkovModel hmm = (HiddenMarkovModel) ois.readObject();
      decoder = new HmmDecoder(hmm);
    } catch (IOException | ClassNotFoundException e) {
      throw new ResourceInitializationException(e);
    }
    // lemmatizer
    // dependency parser
    return ret;
  }

  @Override
  public List<Token> tokenize(JCas jcas) {
    char[] cs = jcas.getDocumentText().toCharArray();
    Tokenizer tokenizer = tokenizerFactory.tokenizer(cs, 0, cs.length);
    return StreamSupport
            .stream(tokenizer.spliterator(), false).map(token -> TypeFactory.createToken(jcas,
                    tokenizer.lastTokenStartPosition(), tokenizer.lastTokenEndPosition()))
            .collect(toList());
  }

  @Override
  public void tagPartOfSpeech(JCas jcas, List<Token> tokens) {
    List<String> tokenTexts = tokens.stream().map(Token::getCoveredText).collect(toList());
    Tagging<String> tagging = decoder.tag(tokenTexts);
    IntStream.range(0, tokens.size()).forEach(i -> tokens.get(i).setPartOfSpeech(tagging.tag(i)));
  }

  @Override
  public void lemmatize(JCas jcas, List<Token> tokens) {
    throw new UnsupportedOperationException();
  }

  @Override
  public void parseDependency(JCas jcas, List<Token> tokens) {
    throw new UnsupportedOperationException();
  }

}
