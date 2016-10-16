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

import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.framework.types.InputElement;
import edu.cmu.lti.oaqa.type.kb.Concept;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * <p>
 *   An implementation of this interface can identify and annotate the {@link Concept}s in document
 *   text in the input {@link JCas}.
 *   {@link edu.cmu.lti.oaqa.baseqa.question.concept.QuestionConceptRecognizer} and
 *   {@link edu.cmu.lti.oaqa.baseqa.evidence.concept.PassageConceptRecognizer} are the two common
 *   uses of this interface.
 *   But this interface is designed to be general to any application beyond QA context.
 * </p>
 * <p>
 *   {@link ConceptSearchProvider} has a different use case where the concept name is known, but
 *   an entry in a concept ontology is needed.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.question.concept.QuestionConceptRecognizer
 * @see edu.cmu.lti.oaqa.baseqa.evidence.concept.PassageConceptRecognizer
 * @see ConceptSearchProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/4/15
 */
public interface ConceptProvider extends Resource {

  List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException;

  default List<Concept> getConcepts(List<JCas> jcases) throws AnalysisEngineProcessException {
    List<Concept> concepts = new ArrayList<>();
    for (JCas jcas : jcases) {
      concepts.addAll(getConcepts(jcas));
    }
    return concepts;
  }

  default List<Concept> getConcepts(List<String> texts, String viewNamePrefix)
          throws AnalysisEngineProcessException {
    JCas jcas;
    try {
      jcas = JCasFactory.createJCas();
    } catch (UIMAException e) {
      throw new AnalysisEngineProcessException(e);
    }
    List<JCas> views = texts.stream().map(text -> {
      String uuid = UUID.randomUUID().toString();
      JCas view = ViewType.createView(jcas, viewNamePrefix, uuid, text);
      InputElement ie = new InputElement(view, 0, text.length());
      ie.setDataset("N/A");
      ie.setQuuid(UUID.randomUUID().toString());
      ie.addToIndexes();
      return view;
    } ).collect(Collectors.toList());
    return getConcepts(views);
  }

}
