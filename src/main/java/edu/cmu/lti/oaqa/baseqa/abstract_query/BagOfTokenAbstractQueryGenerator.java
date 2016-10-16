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

import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.type.retrieval.AtomicQueryConcept;
import edu.cmu.lti.oaqa.util.TypeConstants;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.List;
import java.util.Objects;

import static java.util.stream.Collectors.toList;

/**
 * Create an {@link edu.cmu.lti.oaqa.type.retrieval.AbstractQuery} by grouping only the {@link Token}s.
 *
 * @see TokenSelectionAbstractQueryGenerator
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/4/14
 */
public class BagOfTokenAbstractQueryGenerator extends JCasAnnotator_ImplBase {

  private static final TypeConstants.ConceptType CONCEPT_TYPE = TypeConstants.ConceptType.KEYWORD_TYPE;

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    List<AtomicQueryConcept> qconcepts = tokens.stream().map(token -> {
      String originalText = token.getCoveredText();
      String text = Objects.toString(token.getLemmaForm(), originalText);
      return TypeFactory.createAtomicQueryConcept(jcas, CONCEPT_TYPE, text, originalText);
    } ).collect(toList());
    TypeFactory.createAbstractQuery(jcas, qconcepts).addToIndexes();
  }

}
