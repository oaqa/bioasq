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

import static java.util.stream.Collectors.joining;

/**
 * A simple but general {@link QueryStringConstructor} that only adds quotation marks (") around
 * phrases, but does not use any additional operator or structured query.
 * The query strings are compatible with most text-based search engines, e.g. Google, Lucene, and
 * Indri.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public class BagOfPhraseQueryStringConstructor implements QueryStringConstructor {

  protected String phrasePrefix;

  protected String phraseSuffix;

  public BagOfPhraseQueryStringConstructor(String phrasePrefix, String phraseSuffix) {
    this.phrasePrefix = phrasePrefix;
    this.phraseSuffix = phraseSuffix;
  }

  public BagOfPhraseQueryStringConstructor() {
    this.phrasePrefix = "\"";
    this.phraseSuffix = "\"";
  }

  @Override
  public String formatConceptList(Collection<QueryConcept> concepts) {
    return concepts.stream().map(this::formatQueryConcept).collect(joining(" "));
  }

  @Override
  public String formatQueryConcept(String formatQueryField, String formatQueryText) {
    return formatQueryText;
  }

  @Override
  public String formatQueryField(Collection<String> namedEntityTypes, String conceptType) {
    return null;
  }

  @Override
  public String formatAtomicQueryText(String text, String originalText) {
    return text;
  }

  @Override
  public String formatComplexQueryText(QueryOperatorName operatorName,
          Collection<String> operatorArgs, Collection<QueryConcept> operationArgs) {
    switch (operatorName) {
      case PHRASE:
        return phrasePrefix + formatConceptList(operationArgs) + phraseSuffix;
      default:
        return formatConceptList(operationArgs);
    }
  }

}
