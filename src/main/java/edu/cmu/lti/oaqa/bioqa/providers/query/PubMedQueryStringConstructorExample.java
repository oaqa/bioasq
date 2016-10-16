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

import edu.cmu.lti.oaqa.baseqa.providers.query.BagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.AtomicQueryConcept;
import edu.cmu.lti.oaqa.type.retrieval.ComplexQueryConcept;
import org.apache.uima.UIMAException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;

import static edu.cmu.lti.oaqa.util.TypeConstants.ConceptType.KEYWORD_TYPE;
import static edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName.REQUIRED;
import static edu.cmu.lti.oaqa.util.TypeConstants.QueryOperatorName.TIE;
import static edu.cmu.lti.oaqa.util.TypeFactory.*;

/**
 * @see PubMedQueryStringConstructor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public class PubMedQueryStringConstructorExample {

  public static void main(String[] args) throws UIMAException {
    JCas jcas = JCasFactory.createJCas();
    AtomicQueryConcept rheumatoidArthritisConcept = createAtomicQueryConcept(jcas, "Title",
            KEYWORD_TYPE, "Rheumatoid Arthritis", "Rheumatoid Arthritis");
    AtomicQueryConcept genderConcept = createAtomicQueryConcept(jcas, "Title", KEYWORD_TYPE,
            "gender", "gender");
    AtomicQueryConcept maleConcept = createAtomicQueryConcept(jcas, "Title", KEYWORD_TYPE, "male",
            "male");
    AtomicQueryConcept femaleConcept = createAtomicQueryConcept(jcas, "Title", KEYWORD_TYPE,
            "female", "female");
    ComplexQueryConcept orConcept = createComplexQueryConcept(jcas, KEYWORD_TYPE,
            createQueryOperator(jcas, TIE), genderConcept, maleConcept, femaleConcept);
    ComplexQueryConcept andConcept = createComplexQueryConcept(jcas, KEYWORD_TYPE,
            createQueryOperator(jcas, REQUIRED), rheumatoidArthritisConcept, orConcept);
    AbstractQuery aquery = createAbstractQuery(jcas, andConcept);
    BagOfPhraseQueryStringConstructor bopQueryStringConstructor = new BagOfPhraseQueryStringConstructor();
    System.out.println("Bag of phrases: " + bopQueryStringConstructor.construct(aquery));
    PubMedQueryStringConstructor pubmedQueryStringConstructor = new PubMedQueryStringConstructor();
    System.out.println("PubMed: " + pubmedQueryStringConstructor.construct(aquery));
  }

}
