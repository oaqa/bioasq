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

import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toSet;

/**
 * This implementation of {@link YesNoScorer} considers the different token mentions between the
 * original question and the relevant passages.
 * Different from {@link ConceptOverlapYesNoScorer}, the raw lemma form of the token, instead of
 * the concept type, is used.
 *
 * @see ConceptOverlapYesNoScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class TokenOverlapYesNoScorer extends ConfigurableProvider implements YesNoScorer {

  private Set<String> stoplist;

  private String viewNamePrefix;

  private static final Pattern WORD_PATTERN = Pattern.compile("\\w+");

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    String stoplistPath = String.class.cast(getParameterValue("stoplist-path"));
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistPath), UTF_8).stream()
              .map(String::trim).collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    viewNamePrefix = String.class.cast(getParameterValue("view-name-prefix"));
    return true;
  }

  @Override
  public Map<String, Double> score(JCas jcas) throws AnalysisEngineProcessException {
    List<JCas> views = ViewType.listViews(jcas, viewNamePrefix);
    List<Double> overlaps = new ArrayList<>();
    Set<String> qtokens = TypeUtil.getOrderedTokens(jcas).stream().map(Token::getLemmaForm)
            .filter(s -> WORD_PATTERN.matcher(s).find()).filter(s -> !stoplist.contains(s))
            .collect(toSet());
    for (JCas view : views) {
      long overlap = TypeUtil.getOrderedTokens(view).stream().map(Token::getLemmaForm)
              .filter(s -> WORD_PATTERN.matcher(s).find()).filter(s -> !stoplist.contains(s))
              .filter(qtokens::contains).count();
      overlaps.add((double) overlap / qtokens.size());
    }
    return YesNoScorer.aggregateFeatures(overlaps, "token-overlap");
  }

}
