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

import edu.cmu.lti.oaqa.type.retrieval.QueryConcept;
import edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName;

import java.util.Collection;
import java.util.stream.Collectors;

/**
 * A simple but general {@link QueryStringConstructor} that only adds quotation marks (") around
 * phrases, and uses <tt>AND</tt>, <tt>OR</tt>, and <tt>OR</tt> operators for <tt>REQUIRED</tt>,
 * <tt>TIE</tt>, and <tt>SYNONYM</tt> semantics.
 * Parentheses are also used to nest the hierarchy of operations.
 * The query strings are compatible with Lucene.
 *
 * @see LuceneQueryStringConstructor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/19/14
 */
public class BooleanBagOfPhraseQueryStringConstructor extends BagOfPhraseQueryStringConstructor {

  private static final String PHRASE_PREFIX = "\"";

  private static final String PHRASE_SUFFIX = "\"";

  private static final String STRUCTURE_PREFIX = "(";

  private static final String STRUCTURE_SUFFIX = ")";

  private static final String REQUIRED_TOKEN = "AND";

  private static final String TIE_TOKEN = "OR";

  private static final String SYNONYM_TOKEN = "OR";

  public BooleanBagOfPhraseQueryStringConstructor() {
    super(PHRASE_PREFIX, PHRASE_SUFFIX);
  }

  @Override
  public String formatComplexQueryText(QueryOperatorName operatorName,
          Collection<String> operatorArgs, Collection<QueryConcept> operationArgs) {
    switch (operatorName) {
      case PHRASE:
        return PHRASE_PREFIX + formatConceptList(operationArgs) + PHRASE_SUFFIX;
      case REQUIRED:
        return STRUCTURE_PREFIX + formatComplexConceptList(operationArgs, REQUIRED_TOKEN)
                + STRUCTURE_SUFFIX;
      case TIE:
        return STRUCTURE_PREFIX + formatComplexConceptList(operationArgs, TIE_TOKEN)
                + STRUCTURE_SUFFIX;
      case SYNONYM:
        return STRUCTURE_PREFIX + formatComplexConceptList(operationArgs, SYNONYM_TOKEN)
                + STRUCTURE_SUFFIX;
      default:
        return formatConceptList(operationArgs);
    }
  }

  private String formatComplexConceptList(Collection<QueryConcept> concepts, String operatorName) {
    return concepts.stream().map(this::formatQueryConcept)
            .collect(Collectors.joining(" " + operatorName + " "));
  }

}
