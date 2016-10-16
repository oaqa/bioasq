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

package edu.cmu.lti.oaqa.baseqa.question.yesno;

import com.google.common.collect.Lists;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.LexicalAnswerType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * <p>
 *   This {@link JCasAnnotator_ImplBase} converts the <tt>YESNO</tt> questions to <tt>FACTOID</tt>
 *   questions, i.e. question inversion.
 *   It is used as the first step in the alternate answer generation pipeline in
 *   {@link edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers.AlternateAnswerYesNoScorer}.
 * </p>
 * <p>
 *   It has six steps.
 *   <ol>
 *     <li>Extract the last {@link ConceptMention} and use it as the expected answer.</li>
 *     <li>
 *       Add the {@link ConceptType}s of the expected answer as the
 *       {@link edu.cmu.lti.oaqa.type.answer.AnswerType}s.
 *     </li>
 *     <li>Add {@link CandidateAnswerVariant}s.</li>
 *     <li>Delete last {@link ConceptMention} and its corresponding {@link Token}s.</li>
 *     <li>Delete {@link ConceptMention}s from {@link Concept}s.</li>
 *     <li>Change question type to <tt>FACTOID</tt>.</li>
 *   </ol>
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.answer.yesno.scorers.AlternateAnswerYesNoScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/8/16
 */
public class YesNoToFactoidQuestionConverter extends JCasAnnotator_ImplBase {

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // assume the last concept mention is the expected answer
    ConceptMention lastCmention = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .min(Comparator.comparingInt(ConceptMention::getEnd).reversed()
                    .thenComparingInt(ConceptMention::getBegin)).orElse(null);
    if (lastCmention == null) {
      System.out.println("No last concept mention found.");
      TypeFactory.createLexicalAnswerType(jcas, "null").addToIndexes();
      return;
    }
    System.out.println("Identified expected answer: " + lastCmention.getCoveredText());
    int begin = lastCmention.getBegin();
    int end = lastCmention.getEnd();
    // add the concept types of the expected answer as answer types
    List<Concept> concepts = TypeUtil.getOrderedConceptMentions(jcas).stream()
            .filter(cmention -> cmention.getBegin() == begin && cmention.getEnd() == end)
            .map(ConceptMention::getConcept).collect(toList());
    List<LexicalAnswerType> ats = concepts.stream().map(TypeUtil::getConceptTypes)
            .flatMap(Collection::stream).map(ConceptType::getAbbreviation).distinct()
            .map(ctype -> TypeFactory.createLexicalAnswerType(jcas, ctype)).collect(toList());
    ats.forEach(LexicalAnswerType::addToIndexes);
    System.out.println("Identified answer types: " +
            ats.stream().map(LexicalAnswerType::getLabel).collect(toList()));
    // add candidate answer variant
    CandidateAnswerOccurrence cao = TypeFactory.createCandidateAnswerOccurrence(jcas, begin, end);
    List<String> names = concepts.stream().map(TypeUtil::getConceptNames)
            .flatMap(Collection::stream).distinct().collect(toList());
    CandidateAnswerVariant cav = TypeFactory.createCandidateAnswerVariant(jcas,
            Collections.singletonList(cao), names);
    System.out.println("Added CAV: " + names);
    cav.addToIndexes();
    // remove last concept mention and its corresponding tokens
    List<ConceptMention> coveredCmentions = Lists
            .newArrayList(lastCmention); // selectCovered doesn't select the current annotation
    JCasUtil.selectCovered(ConceptMention.class, lastCmention).forEach(coveredCmentions::add);
    coveredCmentions.forEach(ConceptMention::removeFromIndexes);
    System.out.println("Removed concept mentions: " +
            coveredCmentions.stream().map(ConceptMention::getCoveredText).collect(toList()));
    List<Token> coveredTokens = JCasUtil.selectCovered(Token.class, lastCmention);
    coveredTokens.forEach(Token::removeFromIndexes);
    System.out.println("Removed tokens: " +
            coveredTokens.stream().map(Token::getCoveredText).collect(toList()));
    // remove concept mentions from concepts
    for (ConceptMention cmention : coveredCmentions) {
      Concept concept = cmention.getConcept();
      Collection<ConceptMention> cmentions = TypeUtil.getConceptMentions(jcas);
      cmentions.remove(cmention);
      if (cmentions.isEmpty()) {
        concept.removeFromIndexes();
      } else {
        concept.setMentions(FSCollectionFactory.createFSList(jcas, cmentions));
      }
    }
    // change question type
    TypeUtil.getQuestion(jcas).setQuestionType("FACTOID");
  }

}
