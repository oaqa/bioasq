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

package edu.cmu.lti.oaqa.bioqa.providers.query;

import com.google.common.base.Strings;
import edu.cmu.lti.oaqa.baseqa.providers.query.BooleanBagOfPhraseQueryStringConstructor;

import java.util.Collection;

/**
 * This {@link edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor} extends the
 * {@link BooleanBagOfPhraseQueryStringConstructor} to allow to include field information within
 * pairs of square brackets for <a href="https://www.ncbi.nlm.nih.gov/books/NBK3827/">PubMed</a>
 * search.
 *
 * @see BooleanBagOfPhraseQueryStringConstructor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public class PubMedQueryStringConstructor extends BooleanBagOfPhraseQueryStringConstructor {

  public static final String FIELD_PREFIX = "[";

  public static final String FIELD_SUFFIX = "]";

  public PubMedQueryStringConstructor() {
    super();
  }

  @Override
  public String formatQueryConcept(String formatQueryField, String formatQueryText) {
    return formatQueryText + Strings.nullToEmpty(formatQueryField);
  }

  @Override
  public String formatQueryField(Collection<String> namedEntityTypes, String conceptType) {
    return namedEntityTypes.isEmpty() ? null
            : (FIELD_PREFIX + namedEntityTypes.stream().findFirst().get() + FIELD_SUFFIX);
  }

}
