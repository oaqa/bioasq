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

package edu.cmu.lti.oaqa.baseqa.passage.rerank.scorers;

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import org.apache.uima.jcas.JCas;

import java.util.Map;

/**
 * An instance of an {@link AbstractScorer} for {@link Passage}s that produces binary features for
 * the meta info, such as section label, begin offset, end offset, and the length of the passage.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/6/16
 */
public class MetaInfoPassageScorer extends AbstractScorer<Passage> {

  private static final int WIDTH = 10;

  @Override
  public Map<String, Double> score(JCas jcas, Passage result) {
    int begin = result.getOffsetInBeginSection();
    int end = result.getOffsetInEndSection();
    return ImmutableMap
            .of("section-label/" + result.getBeginSection(), 1.0, "begin-offset/" + begin / WIDTH,
                    1.0, "end-offset/" + end / WIDTH, 1.0, "length/" + (end - begin) / WIDTH, 1.0);
  }

}
