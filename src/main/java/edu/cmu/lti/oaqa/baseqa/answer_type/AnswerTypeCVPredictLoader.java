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

package edu.cmu.lti.oaqa.baseqa.answer_type;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import com.google.common.base.Charsets;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * A {@link JCasAnnotator_ImplBase} that loads predicted answer type file produced by
 * {@link AnswerTypeClassifierTrainer} during the training and CV process.
 *
 * TODO: It may be merged with the general-purpose {@link edu.cmu.lti.oaqa.baseqa.learning_base.CVPredictLoader}
 *
 * @see AnswerTypeClassifierTrainer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/29/15
 */
public class AnswerTypeCVPredictLoader extends JCasAnnotator_ImplBase {

  private Map<String, List<String>> qid2lats;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context,
            "cv-predict-file");
    List<String> lines;
    try {
      lines = Resources.readLines(getClass().getResource(cvPredictFile), Charsets.UTF_8);
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    qid2lats = lines.stream().map(line -> line.split("\t")).collect(toMap(segs -> segs[0],
            segs -> Arrays.stream(segs[1].split(";")).collect(toList()), (x, y) -> x));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String id = TypeUtil.getQuestion(jcas).getId();
    List<String> lats = qid2lats.get(id);
    lats.stream().map(lat -> TypeFactory.createLexicalAnswerType(jcas, lat))
            .forEachOrdered(LexicalAnswerType::addToIndexes);
  }

}
