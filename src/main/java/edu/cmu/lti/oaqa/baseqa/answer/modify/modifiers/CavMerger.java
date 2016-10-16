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

package edu.cmu.lti.oaqa.baseqa.answer.modify.modifiers;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeConstants;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.jgrapht.UndirectedGraph;
import org.jgrapht.alg.ConnectivityInspector;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.SimpleGraph;

import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A {@link CavModifier} that merges {@link CandidateAnswerVariant}s if they share a same variant
 * name, and produces {@link Answer}s for the groups of {@link CandidateAnswerVariant}s. A connected
 * graph identification algorithm is used from {@link ConnectivityInspector}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class CavMerger extends ConfigurableProvider implements CavModifier {

  @Override
  public boolean accept(JCas jcas) throws AnalysisEngineProcessException {
    return true;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void modify(JCas jcas) throws AnalysisEngineProcessException {
    Collection<CandidateAnswerVariant> cavs = TypeUtil.getCandidateAnswerVariants(jcas);
    UndirectedGraph<Object, DefaultEdge> graph = new SimpleGraph<>(DefaultEdge.class);
    cavs.forEach(cav -> {
      graph.addVertex(cav);
      for (String name : TypeUtil.getCandidateAnswerVariantNames(cav)) {
        graph.addVertex(name);
        graph.addEdge(cav, name);
      }
    } );
    ConnectivityInspector<Object, DefaultEdge> ci = new ConnectivityInspector<>(graph);
    ci.connectedSets().stream().map(subgraph -> {
      List<CandidateAnswerVariant> subCavs = subgraph.stream()
              .filter(CandidateAnswerVariant.class::isInstance)
              .map(CandidateAnswerVariant.class::cast).collect(toList());
      return TypeFactory.createAnswer(jcas, TypeConstants.SCORE_UNKNOWN, subCavs);
    } ).forEach(Answer::addToIndexes);
  }

}
