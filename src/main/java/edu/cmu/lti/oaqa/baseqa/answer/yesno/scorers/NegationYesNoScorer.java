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

import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * This implementation of {@link YesNoScorer} uses a list of common English negation words for
 * negation detection from the parameter <tt>negation-cues-path</tt>.
 * Intuitively, a high overlapping count with a high negative or negation count indicates that the
 * original statement tends to be incorrect.
 *
 * @see SentimentYesNoScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class NegationYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  private Set<String> negationCues;

  private String viewNamePrefix;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    String negationCuesPath = (String) getParameterValue("negation-cues-path");
    try {
      negationCues = Resources.readLines(getClass().getResource(negationCuesPath), Charsets.UTF_8)
              .stream().collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    viewNamePrefix = String.class.cast(getParameterValue("view-name-prefix"));
    return true;
  }

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Integer> negationCuesCounts = new ArrayList<>();
    Map<String, Double> features = new HashMap<>();
    for (JCas view : views) {
      Set<String> tokens = TypeUtil.getOrderedTokens(view).stream().flatMap(
              token -> Stream.of(token.getLemmaForm(), token.getCoveredText().toLowerCase()))
              .collect(toSet());
      Set<String> containedNegationCues = Sets.intersection(tokens, negationCues);
      negationCuesCounts.add(containedNegationCues.isEmpty() ? 0 : 1);
      containedNegationCues.forEach(cue -> features.put("negation-cue@" + cue, 1.0));
    }
    features.putAll(YesNoScorer.aggregateFeatures(negationCuesCounts, "negation-cues"));
    return features;
  }

}
