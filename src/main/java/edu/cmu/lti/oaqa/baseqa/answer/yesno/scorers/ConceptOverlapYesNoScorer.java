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

package edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers;

import com.google.common.collect.*;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;

import java.util.Map;
import java.util.Set;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toSet;

/**
 * <p>
 *   This {@link YesNoScorer} captures the effect of "contradictory" concept mentions in the
 *   relevant passages, based on the hypothesis that if a statement is wrong, then the relevant
 *   passages should contain some statements that are contradictory to the original statement, with
 *   some mentions of "contradictory" concepts or "antonyms".
 * </p>
 * <p>
 *   This implementation simplifies the problem by identifying all the different {@link Concept} in
 *   the passages that have the same semantic type as each {@link Concept} in the original question.
 *   For a given concept type, the more the unique concepts are found in both question and relevant
 *   passages, or the less the concepts in the questions are found in the passages, the more likely
 *   the original statement is wrong.
 * </p>
 * <p>
 *   For a concept type, if the number of {@link Concept}s in all relevant passages that belong to
 *   this type is <tt>x</tt>, and the number of {@link Concept}s in the question that belong to
 *   this type is <tt>y</tt>, the "contradictory" score is defined as <tt>a/(a+b)</tt>.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class ConceptOverlapYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    // create ctype2concepts maps and concept counts in question and snippets
    SetMultimap<String, Concept> ctype2concepts = HashMultimap.create();
    Multiset<Concept> concept2count = HashMultiset.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      TypeUtil.getConceptTypes(concept).stream().map(ConceptType::getAbbreviation)
              .forEach(ctype -> ctype2concepts.put(ctype, concept));
      long count = TypeUtil.getConceptMentions(concept).stream()
              .map(cmention -> cmention.getView().getViewName()).distinct().count();
      concept2count.setCount(concept, (int) count);
    }
    Set<Concept> qconcepts = TypeUtil.getConceptMentions(jcas).stream()
            .map(ConceptMention::getConcept).collect(toSet());
    // prepare cross-ctype counts
    ImmutableMap.Builder<String, Double> features = ImmutableMap.builder();
    ListMultimap<String, Double> keyword2values = ArrayListMultimap.create();
    for (String ctype : ctype2concepts.keySet()) {
      Set<Concept> concepts = ctype2concepts.get(ctype);
      // local counts
      int[] totalCounts = concepts.stream().mapToInt(concept2count::count).toArray();
      double[] questionCounts = concepts.stream()
              .mapToDouble(concept -> qconcepts.contains(concept) ? 1 : 0).toArray();
      double[] questionRatios = IntStream.range(0, concepts.size())
              .mapToDouble(i -> questionCounts[i] / totalCounts[i]).toArray();
      double[] passageRatios = DoubleStream.of(questionRatios).map(r -> 1.0 - r).toArray();
      // create feature counts aggregated for each ctype
      addAvgMaxMinFeatures(questionCounts, features, keyword2values, "question-count", ctype);
      addAvgMaxMinFeatures(questionRatios, features, keyword2values, "question-ratio", ctype);
      addAvgMaxMinFeatures(passageRatios, features, keyword2values, "passage-ratio", ctype);
      double questionRatioAvgMicro =
              DoubleStream.of(questionCounts).sum() / IntStream.of(totalCounts).sum();
      features.put("question-ratio-avg-micro@" + ctype, questionRatioAvgMicro);
      keyword2values.put("question-ratio-avg-micro", questionRatioAvgMicro);
      double passageRatioAvgMicro = 1.0 - questionRatioAvgMicro;
      features.put("passage-ratio-avg-macro@" + ctype, passageRatioAvgMicro);
      keyword2values.put("passage-ratio-avg-macro", passageRatioAvgMicro);
    }
    // global features
    keyword2values.asMap().entrySet().stream()
            .map(e -> YesNoScorer.aggregateFeatures(e.getValue(), e.getKey()))
            .forEach(features::putAll);
    return features.build();
  }

  private static void addAvgMaxMinFeatures(double[] values,
          ImmutableMap.Builder<String, Double> features,
          Multimap<String, Double> keyword2values, String keyword, String ctype) {
    double avg = DoubleStream.of(values).average().orElse(0);
    features.put(keyword + "-avg@" + ctype, avg);
    keyword2values.put(keyword + "-avg", avg);
    double max = DoubleStream.of(values).max().orElse(0);
    features.put(keyword + "-max@" + ctype, max);
    keyword2values.put(keyword + "-max", max);
    double min = DoubleStream.of(values).min().orElse(0);
    features.put(keyword + "-min@" + ctype, min);
    keyword2values.put(keyword + "-min", min);
  }

}
