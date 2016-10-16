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

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.google.common.io.Resources;
import com.google.gson.Gson;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.input.Question;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * <p>
 *   As a necessary preprocessing step, this abstract class is intended to label the gold standard
 *   answer type for each gold standard answer.
 *   Classes should override the {@link #annotateConceptTypesForGSAnswers(List)} method to decorate
 *   the answer type for each given pair of question and gold standard answers.
 * </p>
 * <p>
 *   Two special answer types (indeed question types) are defined: <tt>_CHOICE</tt> and
 *   <tt>_QUANTITY</tt>, and both can be identified from hardwired rules, rather than inherited
 *   classes.
 *   However, one can define the <tt>quantity-question-words-path</tt> to expand the cue word list
 *   for <tt>_QUANTITY</tt> questions.
 * </p>
 * <p>
 *   <tt>at-gslabel-file</tt> should be specified and used to create a file containing the labels.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/16
 */
public abstract class GSAnswerTypeLabeler extends JCasAnnotator_ImplBase {

  private List<List<String>> quantityQuestionPhrases;

  private String atGslabelFile;

  private int batchSize;

  private List<QuestionAnswerTypes> completeQats;

  private List<QuestionAnswerTypes> pendingQats;

  private static final String QUANTITY_LABEL = "_QUANTITY";

  private static final String CHOICE_LABEL = "_CHOICE";

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String quantityQuestionWordsPath = UimaContextHelper.getConfigParameterStringValue(context,
            "quantity-question-words-path");
    try {
      quantityQuestionPhrases = Resources
              .readLines(getClass().getResource(quantityQuestionWordsPath), Charsets.UTF_8).stream()
              .map(line -> Arrays.asList(line.split(" "))).collect(toList());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    atGslabelFile = UimaContextHelper.getConfigParameterStringValue(context, "at-gslabel-file");
    batchSize = UimaContextHelper.getConfigParameterIntValue(context, "batch-size", 1);
    completeQats = new ArrayList<>();
    pendingQats = new ArrayList<>();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // prepare input
    Question question = TypeUtil.getQuestion(jcas);
    String qid = question.getId();
    QuestionAnswerTypes qat = new QuestionAnswerTypes(qid, question.getText());
    List<String> answers = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).stream()
            .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(List::stream).map(String::trim)
            .filter(variant -> variant.length() > 0).collect(toList());
    // identify answer semantic type
    List<String> lemmas = TypeUtil.getOrderedTokens(jcas).stream().map(Token::getLemmaForm)
            .collect(toList());
    boolean quantity = quantityQuestionPhrases.stream()
            .map(phrase -> Collections.indexOfSubList(lemmas, phrase)).filter(index -> index >= 0)
            .findAny().isPresent();
    if (quantity) {
      answers.forEach(answer -> qat.addAnswerType(answer, QUANTITY_LABEL));
      completeQats.add(qat);
      return;
    }
    boolean choice = (lemmas.get(0).equals("do") || lemmas.get(0).equals("be"))
            && lemmas.contains("or");
    if (choice) {
      answers.forEach(answer -> qat.addAnswerType(answer, CHOICE_LABEL));
      completeQats.add(qat);
      return;
    }
    // request answer type lookup
    answers.forEach(answer -> qat.addAnswerTypes(answer, new HashSet<>()));
    pendingQats.add(qat);
    if (pendingQats.size() > batchSize) {
      annotateConceptTypesForGSAnswers(pendingQats);
      completeQats.addAll(pendingQats);
      pendingQats.clear();
    }
  }

  protected abstract void annotateConceptTypesForGSAnswers(List<QuestionAnswerTypes> qats)
          throws AnalysisEngineProcessException;

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (pendingQats.size() > 0) {
      annotateConceptTypesForGSAnswers(pendingQats);
      completeQats.addAll(pendingQats);
    }
    try {
      Gson gson = QuestionAnswerTypes.getGson();
      BufferedWriter writer = Files.newWriter(new File(atGslabelFile), Charsets.UTF_8);
      gson.toJson(completeQats, List.class, writer);
      writer.close();
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

}
