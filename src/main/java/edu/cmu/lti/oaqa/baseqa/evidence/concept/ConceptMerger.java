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

package edu.cmu.lti.oaqa.baseqa.evidence.concept;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.*;

import com.google.common.collect.*;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import com.google.common.base.CharMatcher;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * <p>
 *   In a QA pipeline, {@link Concept}s are annotated in the question using a
 *   {@link edu.cmu.lti.oaqa.baseqa.question.concept.QuestionConceptRecognizer}, and in the passages
 *   using a {@link PassageConceptRecognizer}, and various
 *   {@link edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider}s can be integrated into one or
 *   both of the stages, which may result in the same concept being annotated multiple times by
 *   different annotators.
 * </p>
 * <p>
 *   This {@link JCasAnnotator_ImplBase} aims to group these overlapping/duplicated {@link Concept}s
 *   into disconnected {@link Concept}s using a connected graph identification algorithm.
 *   Specifically, two {@link Concept}s are combined if they share either the same Concept ID or the
 *   same Concept name.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/19/25
 */
public class ConceptMerger extends JCasAnnotator_ImplBase {

  private boolean includeDefaultView;

  private String viewNamePrefix;

  private boolean useName;

  private static final String UUID_PREFIX = "__UUID__";

  private static CharMatcher alphaNumeric = CharMatcher.JAVA_LETTER_OR_DIGIT;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    includeDefaultView = UimaContextHelper.getConfigParameterBooleanValue(context,
            "include-default-view", true);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix",
            null);
    useName = UimaContextHelper.getConfigParameterBooleanValue(context, "use-name", true);
  }

  @SuppressWarnings("unchecked")
  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // create views and get all concepts in the views
    List<JCas> views = new ArrayList<>();
    if (includeDefaultView) {
      views.add(jcas);
    }
    views.addAll(ViewType.listViews(jcas, viewNamePrefix));
    List<Concept> concepts = views.stream().map(TypeUtil::getConcepts).flatMap(Collection::stream)
            .collect(toList());
    // preserve concept fields
    Set<String> uuids = new HashSet<>();
    SetMultimap<String, String> uuid2ids = HashMultimap.create();
    SetMultimap<String, String> uuid2names = HashMultimap.create();
    SetMultimap<String, String> uuid2uris = HashMultimap.create();
    SetMultimap<String, ConceptMention> uuid2mentions = HashMultimap.create();
    SetMultimap<String, List<String>> uuid2types = HashMultimap.create();
    for (Concept concept : concepts) {
      String uuid = UUID_PREFIX + UUID.randomUUID().toString();
      uuids.add(uuid);
      uuid2ids.putAll(uuid, TypeUtil.getConceptIds(concept));
      uuid2names.putAll(uuid, TypeUtil.getConceptNames(concept));
      uuid2uris.putAll(uuid, TypeUtil.getConceptUris(concept));
      uuid2mentions.putAll(uuid, TypeUtil.getConceptMentions(concept));
      // also remove duplicated concept type entries
      TypeUtil.getConceptTypes(concept).forEach(type -> uuid2types.put(uuid, toTypeList(type)));
    }
    // connectivity detection for merging
    UndirectedGraph<String, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
    uuids.forEach(graph::addVertex);
    uuid2ids.values().forEach(graph::addVertex);
    uuid2ids.entries().forEach(entry -> graph.addEdge(entry.getKey(), entry.getValue()));
    if (useName) {
      uuid2names.values().stream().map(ConceptMerger::nameKey).forEach(graph::addVertex);
      uuid2names.entries().forEach(entry -> graph.addEdge(entry.getKey(), nameKey(entry.getValue())));
    }
    views.forEach(view -> view.removeAllIncludingSubtypes(Concept.type));
    ConnectivityInspector<String, DefaultEdge> ci = new ConnectivityInspector<>(graph);
    Multiset<Integer> mergedSizes = HashMultiset.create();
    List<Concept> mergedConcepts = ci.connectedSets().stream().map(subgraph -> {
      Set<String> cuuids = subgraph.stream().filter(str -> str.startsWith(UUID_PREFIX))
              .collect(toSet());
      List<String> ids = cuuids.stream().map(uuid2ids::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().collect(toList());
      List<String> names = cuuids.stream().map(uuid2names::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().collect(toList());
      List<String> uris = cuuids.stream().map(uuid2uris::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().collect(toList());
      List<ConceptType> types = cuuids.stream().map(uuid2types::get).flatMap(Set::stream)
              .filter(Objects::nonNull).distinct().map(type -> parseTypeList(jcas, type))
              .collect(toList());
      List<ConceptMention> mentions = cuuids.stream().map(uuid2mentions::get).flatMap(Set::stream)
              .filter(Objects::nonNull).collect(toList());
      mergedSizes.add(cuuids.size());
      return TypeFactory.createConcept(jcas, names, uris, ImmutableList.copyOf(ids), mentions,
              types);
    }).collect(toList());
    mergedConcepts.forEach(Concept::addToIndexes);
    System.out.println("Merged concepts from " + mergedSizes + " concepts.");
    mergedConcepts.stream().map(c -> " - " + TypeUtil.toString(c)).forEach(System.out::println);
  }

  private static String nameKey(String name) {
    return alphaNumeric.retainFrom(name.toLowerCase());
  }

  private static List<String> toTypeList(ConceptType type) {
    return Arrays.asList(type.getId(), type.getName(), type.getAbbreviation());
  }

  private static ConceptType parseTypeList(JCas jcas, List<String> type) {
    return TypeFactory.createConceptType(jcas, type.get(0), type.get(1), type.get(2));
  }

}
