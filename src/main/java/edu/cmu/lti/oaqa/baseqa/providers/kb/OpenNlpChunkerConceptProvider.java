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

package edu.cmu.lti.oaqa.baseqa.providers.kb;

import static java.util.stream.Collectors.toList;

import java.io.InputStream;
import java.util.*;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableSet;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.aliasi.util.Streams;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.Span;

/**
 * A {@link ConceptProvider} that uses <a href="https://opennlp.apache.org/">OpenNLP</a>
 * {@link opennlp.tools.chunker.Chunker} to identify the phrases, and then annotates the sequences
 * of phrases based on the parameter <tt>type</tt> as {@link Concept}s.
 * Example <tt>type</tt>s can be "np", which means "noun phrase", or "np,pp,np", which means "a noun
 * phrase, followed by a prepositional phrase, then another noun phrase"
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/12/16
 */
public class OpenNlpChunkerConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private ChunkerME chunker;

  private List<String> type;

  private int minLength;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String model = String.class.cast(getParameterValue("chunker-model"));
    try (InputStream ois = getClass().getResourceAsStream(model)) {
      chunker = new ChunkerME(new ChunkerModel(ois));
      Streams.closeQuietly(ois);
    } catch (Exception e) {
      throw new ResourceInitializationException(e);
    }
    type = Arrays.asList(String.class.cast(getParameterValue("type")).split(","));
    minLength = Integer.class.cast(getParameterValue("min-length"));
    return ret;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    String[] texts = tokens.stream().map(Token::getCoveredText).toArray(String[]::new);
    String[] pos = tokens.stream().map(Token::getPartOfSpeech).toArray(String[]::new);
    List<Span> spans = insertOutsideSpans(chunker.chunkAsSpans(texts, pos));
    return IntStream.rangeClosed(0, spans.size() - type.size())
            .mapToObj(i -> spans.subList(i, i + type.size()))
            .filter(spansSublist -> type
                    .equals(spansSublist.stream().map(Span::getType).collect(toList())))
            .map(spansSublist -> tokens.subList(spansSublist.get(0).getStart(),
                    spansSublist.get(spansSublist.size() - 1).getEnd()))
            .filter(toks -> toks.size() >= minLength)
            .map(toks -> TypeFactory.createConceptMention(jcas, getFirstTokenBegin(toks),
                    getLastTokenEnd(toks)))
            .map(cmention -> TypeFactory.createConcept(jcas, cmention,
                    TypeFactory.createConceptType(jcas, "opennlp:" + String.join("-", type))))
            .collect(toList());
  }

  private Set<Character> FORBIDDEN_POS_TAG_HEAD_LETTER = ImmutableSet.of('A', 'D', 'P', 'Q', 'W');

  private int getFirstTokenBegin(List<Token> tokens) {
    return FORBIDDEN_POS_TAG_HEAD_LETTER.contains(tokens.get(0).getPartOfSpeech().charAt(0)) ?
            tokens.get(1).getBegin() :
            tokens.get(0).getBegin();
  }

  private int getLastTokenEnd(List<Token> tokens) {
    return tokens.get(tokens.size() - 1).getEnd();
  }

  private static List<Span> insertOutsideSpans(Span[] spans) {
    List<Span> spansWithO = new LinkedList<>(Arrays.asList(spans));
    IntStream.range(0, spans.length - 1).filter(i -> spans[i].getEnd() < spans[i + 1].getStart())
            .forEach(i -> spansWithO.add(spansWithO.indexOf(spans[i + 1]),
                    new Span(spans[i].getEnd(), spans[i + 1].getStart(), "O")));
    return spansWithO;
  }

}
