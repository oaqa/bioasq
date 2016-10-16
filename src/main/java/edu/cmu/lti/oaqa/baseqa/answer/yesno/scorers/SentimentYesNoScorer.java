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
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.util.TypeUtil;
import edu.stanford.nlp.util.Sets;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toSet;

/**
 * This implementation of {@link YesNoScorer} estimates the explicit sentiment expression in the
 * the relevant passages.
 * It currently supports a simple dictionary based method for sentiment analysis (via
 * <tt>positive-wordlist-path</tt> and <tt>negative-wordlist-path</tt>), and counts whether and how
 * many times each positive / negative word is mentioned in each passage, then aggregates across the
 * passages using min / max / average.
 *
 * @see NegationYesNoScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class SentimentYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  private Set<String> positiveWords;

  private Set<String> negativeWords;

  private String viewNamePrefix;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    String positiveWordlistPath = (String) getParameterValue("positive-wordlist-path");
    String negativeWordlistPath = (String) getParameterValue("negative-wordlist-path");
    try {
      positiveWords = Resources
              .readLines(getClass().getResource(positiveWordlistPath), Charsets.UTF_8).stream()
              .collect(toSet());
      negativeWords = Resources
              .readLines(getClass().getResource(negativeWordlistPath), Charsets.UTF_8).stream()
              .collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    viewNamePrefix = String.class.cast(getParameterValue("view-name-prefix"));
    return true;
  }

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Integer> negativeWordsCounts = new ArrayList<>();
    List<Integer> positiveWordsCounts = new ArrayList<>();
    Map<String, Double> features = new HashMap<>();
    for (JCas view : views) {
      Set<String> tokens = TypeUtil.getOrderedTokens(view).stream().flatMap(
              token -> Stream.of(token.getLemmaForm(), token.getCoveredText().toLowerCase()))
              .collect(toSet());
      Set<String> containedPositiveWords = Sets.intersection(tokens, positiveWords);
      positiveWordsCounts.add(containedPositiveWords.isEmpty() ? 0 : 1);
      containedPositiveWords.forEach(word -> features.put("sentiment-word@" + word, 1.0));
      Set<String> containedNegativeWords = Sets.intersection(tokens, negativeWords);
      negativeWordsCounts.add(containedNegativeWords.isEmpty() ? 0 : 1);
      containedNegativeWords.forEach(word -> features.put("sentiment-word@" + word, 1.0));
    }
    features.putAll(YesNoScorer.aggregateFeatures(negativeWordsCounts, "negative-words"));
    features.putAll(YesNoScorer.aggregateFeatures(positiveWordsCounts, "positive-words"));
    return features;
  }

}
