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

package edu.cmu.lti.oaqa.baseqa.document.rerank.scorers;

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import org.apache.uima.jcas.JCas;

import java.util.Map;

/**
 * An instance of an {@link AbstractScorer} for {@link Document}s that simply copies
 * the original score from the {@link Document}.
 *
 * @see edu.cmu.lti.oaqa.baseqa.document.retrieval.LuceneDocumentRetrievalExecutor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/6/16
 */
public class OriginalScoreDocumentScorer extends AbstractScorer<Document> {

  @Override
  public Map<String, Double> score(JCas jcas, Document result) {
    return ImmutableMap.of("original/rank", 1.0 / (result.getRank() + 1.0), "original/score",
            result.getScore());
  }

}
