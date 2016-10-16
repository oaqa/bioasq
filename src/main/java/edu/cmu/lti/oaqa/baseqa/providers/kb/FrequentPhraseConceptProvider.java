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

import edu.cmu.cs.ziy.lucene_frequent_phrase.CValuePhraseScorer;
import edu.cmu.cs.ziy.lucene_frequent_phrase.FrequentPhraseExtractor;
import edu.cmu.cs.ziy.lucene_frequent_phrase.Phrase;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * A {@link ConceptProvider} that create {@link Concept}s for all the frequent phrases in a corpus.
 * Since it is based on frequency of phrases, it can only identify candidate {@link Concept}s for
 * relevant {@link edu.cmu.lti.oaqa.type.retrieval.Passage}s (after copied to views by
 * {@link edu.cmu.lti.oaqa.baseqa.evidence.PassageToViewCopier}), but not question.
 *
 * @see edu.cmu.lti.oaqa.baseqa.evidence.concept.PassageConceptRecognizer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/14/16
 */
public class FrequentPhraseConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private int maxLength;

  private int minFreq;

  private double minFreqRatio;

  private String type;

  private double scoreThreshold;

  private double scoreRatioThreshold;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    maxLength = Integer.class.cast(getParameterValue("max-length"));
    minFreq = Integer.class.cast(getParameterValue("min-freq"));
    minFreqRatio = Double.class.cast(getParameterValue("min-freq-ratio"));
    type = String.class.cast(getParameterValue("type"));
    scoreThreshold = Double.class.cast(getParameterValue("score-threshold"));
    scoreRatioThreshold = Double.class.cast(getParameterValue("score-ratio-threshold"));
    return true;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    throw new UnsupportedOperationException(getClass().getCanonicalName() +
            " only works on a collection of texts, use getConcepts(List<JCas> jcases) instead.");
  }

  @Override
  public List<Concept> getConcepts(List<JCas> jcases) throws AnalysisEngineProcessException {
    List<String> texts = jcases.stream().map(JCas::getDocumentText).collect(toList());
    int support = Math.min(minFreq, (int) (minFreqRatio * jcases.size()));
    FrequentPhraseExtractor fpe = new FrequentPhraseExtractor(maxLength, support);
    CValuePhraseScorer ps = new CValuePhraseScorer(CValuePhraseScorer.Type.valueOf(type));
    Map<Phrase, Double> phrase2score;
    try {
      Set<Phrase> phrases = fpe.analyzeTextCollection(texts, new StandardAnalyzer());
      phrase2score = ps.scorePhrase(phrases);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    double maxScore = Collections.max(phrase2score.values());
    List<Concept> concepts = new ArrayList<>();
    JCas refJCas = jcases.get(0);
    for (Map.Entry<Phrase, Double> entry : phrase2score.entrySet()) {
      double score = entry.getValue();
      if (score < scoreThreshold || score < scoreRatioThreshold * maxScore) continue;
      Phrase phrase = entry.getKey();
      List<ConceptMention> cmentions = phrase.getDocIdPositionOffsets().stream()
              .map(ipo -> TypeFactory
                      .createConceptMention(jcases.get(ipo.getDocId()), ipo.getBegin(),
                              ipo.getEnd())).collect(toList());
      Concept concept = TypeFactory.createConcept(refJCas, phrase.getTermsString(), cmentions,
              TypeFactory.createConceptType(refJCas, "frequent-phrase"));
      concepts.add(concept);
    }
    return concepts;
  }

}
