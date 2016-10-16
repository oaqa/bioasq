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

import com.google.common.base.CharMatcher;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.emory.clir.clearnlp.component.mode.dep.DEPConfiguration;
import edu.emory.clir.clearnlp.component.mode.dep.EnglishDEPParser;
import edu.emory.clir.clearnlp.component.mode.morph.EnglishMPAnalyzer;
import edu.emory.clir.clearnlp.component.mode.pos.EnglishPOSTagger;
import edu.emory.clir.clearnlp.dependency.DEPFeat;
import edu.emory.clir.clearnlp.dependency.DEPNode;
import edu.emory.clir.clearnlp.dependency.DEPTree;
import edu.emory.clir.clearnlp.tokenization.EnglishTokenizer;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.tukaani.xz.XZInputStream;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * A {@link ParserProvider} that wraps <a href="https://github.com/clir/clearnlp">ClearNLP</a>
 * parser.
 * One should specify the paths to the POS and dependency models in the descriptor via parameters
 * <tt>pos-model</tt> and <tt>dep-model</tt>.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/12/16
 */
public class ClearParserProvider extends ConfigurableProvider implements ParserProvider {

  private EnglishTokenizer tokenizer;

  private EnglishPOSTagger tagger;

  private EnglishMPAnalyzer mpAnalyzer;

  private EnglishDEPParser parser;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // tokenizer
    tokenizer = new EnglishTokenizer();
    // pos
    String posModelPath = String.class.cast(getParameterValue("pos-model"));
    try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
            new XZInputStream(getClass().getResourceAsStream(posModelPath))))) {
      tagger = new EnglishPOSTagger(ois);
    } catch (IOException e) {
      new ResourceInitializationException(e);
    }
    // lemmatizer
    mpAnalyzer = new EnglishMPAnalyzer();
    // dependency parser
    String depModelPath = String.class.cast(getParameterValue("dep-model"));
    try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(
            new XZInputStream(getClass().getResourceAsStream(depModelPath))))) {
      parser = new EnglishDEPParser(new DEPConfiguration("root"), ois);
    } catch (IOException e) {
      new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public List<Token> tokenize(JCas jcas) {
    String text = jcas.getDocumentText();
    List<String> tokenTexts = tokenizer.tokenize(text);
    int offset = 0;
    List<Token> tokens = new ArrayList<>();
    for (String tokenText : tokenTexts) {
      offset = text.indexOf(tokenText, offset);
      Token tok = TypeFactory.createToken(jcas, offset, offset + tokenText.length());
      tokens.add(tok);
      offset += tokenText.length();
    }
    return tokens;
  }

  @Override
  public void tagPartOfSpeech(JCas jcas, List<Token> tokens) {
    List<String> tokenTexts = tokens.stream().map(Token::getCoveredText).collect(toList());
    DEPTree tree = new DEPTree(tokenTexts);
    tagger.process(tree);
    IntStream.range(0, tokens.size())
            .forEach(i -> tokens.get(i).setPartOfSpeech(tree.get(i + 1).getPOSTag()));
  }

  private static CharMatcher DIGIT_MATCHER = CharMatcher.DIGIT;

  @Override
  public void lemmatize(JCas jcas, List<Token> tokens) {
    tokens.stream().forEach(token -> {
      String text = token.getCoveredText();
      String pos = token.getPartOfSpeech();
      if (DIGIT_MATCHER.matchesAnyOf(text)) {
        token.setLemmaForm(text);
      } else {
        DEPNode node = new DEPNode(-1, text, null, pos, new DEPFeat());
        mpAnalyzer.analyze(node);
        token.setLemmaForm(node.getLemma());
      }
    } );
  }

  @Override
  public void parseDependency(JCas jcas, List<Token> tokens) {
    List<DEPNode> nodes = IntStream.range(0, tokens.size()).mapToObj(i -> {
      Token token = tokens.get(i);
      String word = token.getCoveredText();
      String lemma = token.getLemmaForm();
      String pos = token.getPartOfSpeech();
      return new DEPNode(i + 1, word, lemma, pos, null);
    } ).collect(toList());
    DEPTree tree = new DEPTree(nodes);
    parser.process(tree);
    IntStream.range(0, tokens.size()).forEach(i -> {
      DEPNode node = tree.get(i + 1);
      Token token = tokens.get(i);
      token.setDepLabel(node.getLabel());
      if (!node.isLabel("root") && node.getHead().getID() != 0) {
        token.setHead(tokens.get(node.getHead().getID() - 1));
      }
    } );
  }

}
