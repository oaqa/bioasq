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

import edu.cmu.lti.oaqa.type.retrieval.*;
import edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.fit.util.FSCollectionFactory;

import java.util.Collection;

/**
 * An implementation of this interface translates an environment independent {@link AbstractQuery}
 * to a query string compatible to the syntax.
 * The implementation should deal with different types of {@link QueryConcept}s and different
 * {@link QueryOperator}s, by overriding {@link #formatConceptList(Collection)},
 * {@link #formatQueryConcept(String, String)}, {@link #formatQueryField(Collection, String)},
 * {@link #formatAtomicQueryText(String, String)}, and
 * {@link #formatComplexQueryText(QueryOperatorName, Collection, Collection)}.
 *
 * @see AbstractQuery
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public interface QueryStringConstructor {

  default String construct(AbstractQuery aquery) {
    return formatConceptList(TypeUtil.getQueryConcepts(aquery));
  }

  String formatConceptList(Collection<QueryConcept> concepts);

  default String formatQueryConcept(QueryConcept concept) {
    return formatQueryConcept(formatQueryField(concept), formatQueryText(concept));
  }

  String formatQueryConcept(String formatQueryField, String formatQueryText);

  default String formatQueryField(QueryConcept concept) {
    return formatQueryField(FSCollectionFactory.create(concept.getNamedEntityTypes()),
            concept.getConceptType());
  }

  String formatQueryField(Collection<String> namedEntityTypes, String conceptType);

  default String formatQueryText(QueryConcept concept) {
    if (AtomicQueryConcept.class.isAssignableFrom(concept.getClass())) {
      return formatAtomicQueryText(AtomicQueryConcept.class.cast(concept));
    } else if (ComplexQueryConcept.class.isAssignableFrom(concept.getClass())) {
      return formatComplexQueryText(ComplexQueryConcept.class.cast(concept));
    } else {
      throw new UnsupportedOperationException();
    }
  }

  default String formatAtomicQueryText(AtomicQueryConcept concept) {
    return formatAtomicQueryText(concept.getText(), concept.getOriginalText());
  }

  String formatAtomicQueryText(String text, String originalText);

  default String formatComplexQueryText(ComplexQueryConcept concept) {
    return formatComplexQueryText(concept.getOperator(),
            FSCollectionFactory.create(concept.getOperatorArgs(), QueryConcept.class));
  }

  default String formatComplexQueryText(QueryOperator operator,
                                        Collection<QueryConcept> operatorArgs) {
    return formatComplexQueryText(QueryOperatorName.valueOf(operator.getName()),
            FSCollectionFactory.create(operator.getArgs()), operatorArgs);
  }

  String formatComplexQueryText(QueryOperatorName operatorName, Collection<String> operatorArgs,
                                Collection<QueryConcept> operationArgs);

}
