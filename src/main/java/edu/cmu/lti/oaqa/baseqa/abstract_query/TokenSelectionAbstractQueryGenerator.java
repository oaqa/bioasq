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

package edu.cmu.lti.oaqa.baseqa.abstract_query;

import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.QueryConcept;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Create {@link AbstractQuery} from both the {@link Token}s identified from the sentence.
 * In addition to {@link BagOfTokenAbstractQueryGenerator}, one can specify a list of allowed POS
 * tags (via <tt>pos-tags-path</tt>), a list of noun tags (via <tt>noun-tags-path</tt>), and/or
 * a list of stop words (via <tt>stoplist-path</tt>) to filter the {@link Token}s.
 *
 * @see TokenConceptAbstractQueryGenerator
 * @see BagOfTokenAbstractQueryGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/3/14
 */
public class TokenSelectionAbstractQueryGenerator extends JCasAnnotator_ImplBase {

  private Set<String> posTags;

  private Set<String> nounTags;

  private Set<String> stoplist;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    // get pos tags
    String posTagsPath = UimaContextHelper.getConfigParameterStringValue(context, "pos-tags-path",
            null);
    if (posTagsPath != null) {
      try {
        posTags = Resources.readLines(getClass().getResource(posTagsPath), UTF_8).stream()
                .map(String::trim).collect(toSet());
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // get noun tags
    String nounTagsPath = UimaContextHelper.getConfigParameterStringValue(context, "noun-tags-path",
            null);
    if (nounTagsPath != null) {
      try {
        nounTags = Resources.readLines(getClass().getResource(nounTagsPath), UTF_8).stream()
                .map(String::trim).collect(toSet());
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // get stop word list
    String stoplistPath = UimaContextHelper.getConfigParameterStringValue(context, "stoplist-path",
            null);
    if (stoplistPath != null) {
      try {
        stoplist = Resources.readLines(getClass().getResource(stoplistPath), UTF_8).stream()
                .map(String::trim).collect(toSet());
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    // create an aquery using all tokens with POS in posTags set
    List<QueryConcept> qconcepts = createQueryConceptsFromTokens(jcas, tokens, posTags, stoplist);
    AbstractQuery aquery = TypeFactory.createAbstractQuery(jcas, qconcepts);
    aquery.addToIndexes();
    // create a backup aquery using only nouns
    List<QueryConcept> qconceptsNoun = createQueryConceptsFromTokens(jcas, tokens, nounTags,
            stoplist);
    AbstractQuery aqueryNoun = TypeFactory.createAbstractQuery(jcas, qconceptsNoun);
    aqueryNoun.addToIndexes();
  }

  static List<QueryConcept> createQueryConceptsFromTokens(JCas jcas, List<Token> tokens,
          Set<String> posTags, Set<String> stoplist) {
    // set original text as lemma in case no lemmatizer is provided
    tokens.stream().filter(token -> token.getLemmaForm() == null)
            .forEach(token -> token.setLemmaForm(token.getCoveredText()));
    return tokens.stream()
            .filter(token -> posTags == null || posTags.contains(token.getPartOfSpeech()))
            .filter(token -> stoplist == null
                    || !stoplist.contains(token.getLemmaForm().toLowerCase()))
            .map(token -> {
              String originalText = token.getCoveredText();
              String text = token.getLemmaForm();
              return TypeFactory.createAtomicQueryConcept(jcas, text, originalText);
            } ).collect(toList());
  }

}
