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

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.retrieval.*;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.*;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

/**
 * <p>
 *  A bottom-up {@link ComplexQueryConcept} construction, considering phrases, weights (scores), and
 *  synonyms.
 * </p>
 * <p>
 * Procedure:
 * <br>
 * For each {@link ConceptMention}:
 * <br>
 * <ol>
 *   <li>Get a list of name variants as {@link AtomicQueryConcept}s from each {@link Concept},</li>
 *   <li>Wrap each name in the {@link AtomicQueryConcept}s as a phrase, connected by
 *   {@link edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName#PHRASE} operator,</li>
 *   <li>Create a single {@link ComplexQueryConcept} containing all phrases, connected by
 *   {@link edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName#SYNONYM} operator,</li>
 *   <li>Create a {@link edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName#WEIGHT} query,
 *   connecting the "weight" and specify the weight as an operator's arguments.</li>
 * </ol>
 * </p>
 *
 * @see TokenConceptAbstractQueryGenerator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 11/3/14
 */
public class ConceptAbstractQueryGenerator extends JCasAnnotator_ImplBase {

  private boolean useWeight;

  private boolean useType;

  private boolean required;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    useType = UimaContextHelper.getConfigParameterBooleanValue(context, "use-type", false);
    useWeight = UimaContextHelper.getConfigParameterBooleanValue(context, "use-weight", false);
    required = UimaContextHelper.getConfigParameterBooleanValue(context, "required", false);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<Concept> concepts = TypeUtil.getConcepts(jcas);
    List<QueryConcept> qconcepts = createQueryConceptsFromConceptMentions(jcas, concepts, useType,
            useWeight);
    AbstractQuery aquery = required ?
            TypeFactory.createAbstractQuery(jcas,
                    TypeFactory.createRequiredQueryConcept(jcas, qconcepts)) :
            TypeFactory.createAbstractQuery(jcas, qconcepts);
    aquery.addToIndexes();
  }

  static List<QueryConcept> createQueryConceptsFromConceptMentions(JCas jcas,
          Collection<Concept> concepts, boolean useType, boolean useWeight) {
    // identify name variants in each concept and associate with the maximum score of all mentions.
    Set<Set<String>> keywordsSet = new HashSet<>();
    Map<Set<String>, Double> keywords2max = new HashMap<>();
    Map<Set<String>, Set<String>> keywords2types = new HashMap<>();
    for (Concept concept : concepts) {
      Set<String> names = TypeUtil.getConceptNames(concept).stream().filter(Objects::nonNull)
              .distinct().collect(toSet());
      if (names.isEmpty())
        continue;
      keywordsSet.add(names);
      OptionalDouble maxScore = TypeUtil.getConceptMentions(concept).stream()
              .mapToDouble(ConceptMention::getScore).max();
      Set<String> types = TypeUtil.getConceptTypeNames(concept).stream().collect(toSet());
      if (maxScore.isPresent())
        keywords2max.put(names, maxScore.getAsDouble());
      if (!types.isEmpty())
        keywords2types.put(names, types);
    }
    // create the query concept from the keywords
    List<QueryConcept> ret = new ArrayList<>();
    for (Set<String> keywords : keywordsSet) {
      List<AtomicQueryConcept> atomics = keywords.stream()
              .map(syn -> TypeFactory.createAtomicQueryConcept(jcas, syn)).collect(toList());
      List<ComplexQueryConcept> phrases = atomics.stream()
              .map(atomic -> TypeFactory.createPhraseQueryConcept(jcas, atomic)).collect(toList());
      ComplexQueryConcept synonyms = useType ?
              TypeFactory.createSynonymQueryConcept(jcas, keywords2types.get(keywords), phrases) :
              TypeFactory.createSynonymQueryConcept(jcas, phrases);
      ComplexQueryConcept weight = useWeight ?
              TypeFactory.createWeightQueryConcept(jcas, keywords2max.getOrDefault(keywords, 1D),
                      synonyms) :
              synonyms;
      ret.add(weight);
    }
    return ret;
  }

}
