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

package edu.cmu.lti.oaqa.baseqa.evidence;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toMap;

/**
 * This {@link JCasAnnotator_ImplBase} copies the {@link Passage}s residing in the main view, often
 * as an outcome from a passage retrieval component (e.g.
 * {@link edu.cmu.lti.oaqa.baseqa.passage.retrieval.DocumentToPassageConverter}), to individual
 * views, for subsequent processing of passage texts, e.g.
 * {@link edu.cmu.lti.oaqa.baseqa.evidence.parse.PassageParser} and
 * {@link edu.cmu.lti.oaqa.baseqa.evidence.concept.PassageConceptRecognizer}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.evidence.concept.PassageConceptRecognizer
 * @see edu.cmu.lti.oaqa.baseqa.evidence.parse.PassageParser
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/12/15
 */
public class PassageToViewCopier extends JCasAnnotator_ImplBase {

  private String viewNamePrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Passage> passages = TypeUtil.getRankedPassages(jcas);
    Map<String, String> vid2text = passages.stream().collect(
            toMap(PassageToViewCopier::createPassageViewId, Passage::getText, (x, y) -> x));
    for (Map.Entry<String, String> entry : vid2text.entrySet()) {
      ViewType.createView(jcas, viewNamePrefix, entry.getKey(), entry.getValue());
    }
  }

  private static String createPassageViewId(Passage passage) {
    return passage.getUri() + "/" + passage.getBeginSection() + "/"
            + passage.getOffsetInBeginSection() + "/" + passage.getEndSection() + "/"
            + passage.getOffsetInEndSection();
  }

}
