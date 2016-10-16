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

package edu.cmu.lti.oaqa.baseqa.providers.query;

import com.google.common.base.Strings;
import edu.cmu.lti.oaqa.type.retrieval.QueryConcept;
import edu.cmu.lti.oaqa.util.TypeConstants;
import edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName;
import org.apache.lucene.queryparser.classic.QueryParser;

import java.util.Collection;

/**
 * This {@link QueryStringConstructor} extends {@link BooleanBagOfPhraseQueryStringConstructor}
 * by adding additional Lucene supported query syntax: field and field connector (:) and query term
 * weight and weight connector (^). See <a href="https://lucene.apache.org/core/6_2_1/queryparser/org/apache/lucene/queryparser/classic/package-summary.html#package.description">Lucene Query Syntax</a>
 * for details.
 *
 * @see BooleanBagOfPhraseQueryStringConstructor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class LuceneQueryStringConstructor extends BooleanBagOfPhraseQueryStringConstructor {

  private static final String FIELD_CONNECTOR = ":";

  private static final String WEIGHT_CONNECTOR = "^";

  public LuceneQueryStringConstructor() {
    super();
  }

  @Override
  public String formatAtomicQueryText(String text, String originalText) {
    return QueryParser.escape(text);
  }

  @Override
  public String formatQueryConcept(String formatQueryField, String formatQueryText) {
    return Strings.nullToEmpty(formatQueryField) + formatQueryText;
  }

  @Override
  public String formatQueryField(Collection<String> namedEntityTypes, String conceptType) {
    return namedEntityTypes.isEmpty() ? null
            : (namedEntityTypes.stream().findFirst().get() + FIELD_CONNECTOR);
  }

  @Override
  public String formatComplexQueryText(QueryOperatorName operatorName,
          Collection<String> operatorArgs, Collection<QueryConcept> operationArgs) {
    switch (operatorName) {
      case WEIGHT:
        String weight = operatorArgs.stream().findFirst().get();
        if (weight.equals(String.valueOf(TypeConstants.SCORE_UNKNOWN))) {
          return formatConceptList(operationArgs);
        } else {
          return formatConceptList(operationArgs) + WEIGHT_CONNECTOR +
                  operatorArgs.stream().findFirst().get();
        }
      default:
        return super.formatComplexQueryText(operatorName, operatorArgs, operationArgs);
    }
  }

}
