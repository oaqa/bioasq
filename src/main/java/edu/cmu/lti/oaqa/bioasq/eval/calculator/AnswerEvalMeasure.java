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

package edu.cmu.lti.oaqa.bioasq.eval.calculator;

import edu.cmu.lti.oaqa.baseqa.eval.Measure;

/**
 * Definitions of BioASQ Phase B Factoid, List, and YesNo QA evaluation metrics.
 *
 * @see AnswerEvalCalculator
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
enum AnswerEvalMeasure implements Measure {

  // PER-TOPIC FACTOID QUESTION ANSWER MEASURES
  FACTOID_COUNT, FACTOID_STRICT_RETRIEVED, FACTOID_LENIENT_RETRIEVED, FACTOID_RECIPROCAL_RANK,
  
  // ACCUMULATED FACTOID QUESTION ANSWER MEASURES
  FACTOID_STRICT_ACCURACY, FACTOID_LENIENT_ACCURACY, FACTOID_MRR,
  
  // PER-TOPIC LIST QUESTION ANSWER MEASURES
  LIST_COUNT, LIST_PRECISION, LIST_RECALL, LIST_F1,

  // ACCUMULATED LIST QUESTION ANSWER MEASURES
  LIST_MEAN_PRECISION, LIST_MEAN_RECALL, LIST_MEAN_F1,

  // PER-TOPIC YESNO QUESTION ANSWER MEASURES
  YESNO_CORRECT, YESNO_TRUE_POS, YESNO_TRUE_NEG,

  // ACCUMULATED YESNO QUESTION ANSWER MEASURES
  YESNO_COUNT, YESNO_MEAN_ACCURACY, YESNO_MEAN_POS_ACCURACY, YESNO_MEAN_NEG_ACCURACY;

  static {
    for (AnswerEvalMeasure measure : values()) {
      Measure.name2measure.put(measure.getName(), measure);
    }
  }

  @Override
  public String getName() {
    return name();
  }

}
