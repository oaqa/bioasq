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

import com.google.common.collect.Range;
import com.google.common.collect.RangeSet;
import com.google.common.collect.TreeRangeSet;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
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
import java.util.Collection;
import java.util.List;
import java.util.Set;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * Create {@link AbstractQuery} from both the {@link Token}s (similar to
 * {@link TokenSelectionAbstractQueryGenerator}) and {@link edu.cmu.lti.oaqa.type.kb.ConceptMention}s
 * (similar to {@link ConceptAbstractQueryGenerator}) identified in the input sentence.
 * The {@link Token}s that are enclosed by a {@link edu.cmu.lti.oaqa.type.kb.ConceptMention} are
 * ignored, and a second {@link AbstractQuery} is created using only the nouns.
 *
 * @see ConceptAbstractQueryGenerator
 * @see TokenSelectionAbstractQueryGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/3/14
 */
public class TokenConceptAbstractQueryGenerator extends JCasAnnotator_ImplBase {

  private boolean useType;

  private boolean useWeight;

  private Set<String> posTags;

  private Set<String> nounTags;

  private Set<String> stoplist;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    useType = UimaContextHelper.getConfigParameterBooleanValue(context, "use-type", false);
    useWeight = UimaContextHelper.getConfigParameterBooleanValue(context, "use-weight", false);
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
    Collection<Concept> concepts = TypeUtil.getConcepts(jcas);
    List<QueryConcept> qconcepts = ConceptAbstractQueryGenerator
            .createQueryConceptsFromConceptMentions(jcas, concepts, useType, useWeight);
    // filter tokens that are covered by concept mentions
    RangeSet<Integer> cmentionRanges = TreeRangeSet.create();
    concepts.stream().map(TypeUtil::getConceptMentions).flatMap(Collection::stream)
            .map(cmention -> Range.closedOpen(cmention.getBegin(), cmention.getEnd()))
            .forEach(cmentionRanges::add);
    // create an aquery using all tokens with POS in posTags set
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas).stream().filter(token -> !cmentionRanges
            .encloses(Range.closedOpen(token.getBegin(), token.getEnd()))).collect(toList());
    List<QueryConcept> qconceptTokens = TokenSelectionAbstractQueryGenerator
            .createQueryConceptsFromTokens(jcas, tokens, posTags, stoplist);
    qconceptTokens.addAll(qconcepts);
    AbstractQuery aquery = TypeFactory.createAbstractQuery(jcas, qconceptTokens);
    aquery.addToIndexes();
    // create a backup aquery using only nouns
    List<QueryConcept> qconceptNouns = TokenSelectionAbstractQueryGenerator
            .createQueryConceptsFromTokens(jcas, tokens, nounTags, stoplist);
    qconceptNouns.addAll(qconcepts);
    AbstractQuery aqueryNoun = TypeFactory.createAbstractQuery(jcas, qconceptNouns);
    aqueryNoun.addToIndexes();
  }

}
