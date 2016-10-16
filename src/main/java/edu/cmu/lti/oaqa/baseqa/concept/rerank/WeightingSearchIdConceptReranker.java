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

package edu.cmu.lti.oaqa.baseqa.concept.rerank;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.*;

/**
 * A {@link ConceptSearchResult} reranker that scores each candidate by the weighted combination of
 * the original score and the "source" ontology of each {@link ConceptSearchResult}.
 * The weight is specified in a file with parameter <tt>weights-path</tt>.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a>,
 * <a href="mailto:niloygupta@gmail.com">Niloy Gupta</a> created on 4/25/16
 */
public class WeightingSearchIdConceptReranker extends JCasAnnotator_ImplBase {

  private Map<String, double[]> weights;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String param = UimaContextHelper.getConfigParameterStringValue(context, "weights-path");
    weights = new HashMap<>();
    try (BufferedReader bw = new BufferedReader(
            new InputStreamReader(getClass().getResourceAsStream(param)))) {
      String line;
      while ((line = bw.readLine()) != null) {
        String[] segs = line.split("\t");
        weights.put(segs[0],
                new double[] { Double.parseDouble(segs[1]), Double.parseDouble(segs[2]) });
      }
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<ConceptSearchResult> results = TypeUtil.getRankedConceptSearchResults(jcas);
    for (ConceptSearchResult result : results) {
      double[] params = weights.get(result.getSearchId());
      double wT = params[0] + result.getScore() * params[1];
      result.setScore(Math.exp(wT) / (1 + Math.exp(wT)));
    }
    TypeUtil.rankedSearchResultsByScore(results, results.size());
  }

}
