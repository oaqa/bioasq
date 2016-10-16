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

package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toList;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.uima.UIMARuntimeException;
import org.apache.uima.cas.AnnotationBaseFS;
import org.apache.uima.cas.CASException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.jcas.JCas;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.google.common.collect.Multimap;
import com.google.common.collect.SetMultimap;

import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * An utility class mostly for parse tree related operations.
 *
 * TODO: It may be refactored in a future version.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/16
 */
public class CavUtil {

  public static SetMultimap<Token, Token> getHeadTokenMap(Collection<Token> tokens) {
    SetMultimap<Token, Token> head2children = HashMultimap.create();
    tokens.stream().filter(token -> token.getHead() != null)
            .forEach(token -> head2children.put(token.getHead(), token));
    return head2children;
  }

  public static List<CandidateAnswerOccurrence> getPhraseVariants(JCas jcas, Token token,
          Multimap<Token, Token> head2children, Set<Token> exclusions, int variantLimit) {
    Token head;
    if ((head = token.getHead()) == null) {
      return CavUtil.createCandidateAnswerOccurrencesFromDepBranch(jcas, token, head2children,
              exclusions, variantLimit);
    } else {
      List<CandidateAnswerOccurrence> aoccurrences = new ArrayList<>();
      aoccurrences.add(CavUtil.createCandidateAnswerOccurrence(jcas, Arrays.asList(token, head)));
      aoccurrences.addAll(CavUtil.createCandidateAnswerOccurrencesFromDepBranch(jcas, token,
              head2children, exclusions, variantLimit - 1));
      return aoccurrences;
    }
  }

  public static List<CandidateAnswerOccurrence> createCandidateAnswerOccurrencesFromDepBranch(
          JCas jcas, Token token, Multimap<Token, Token> head2children, int variantLimit) {
    return createCandidateAnswerOccurrencesFromDepBranch(jcas, token, head2children, null,
            variantLimit);
  }

  public static List<CandidateAnswerOccurrence> createCandidateAnswerOccurrencesFromDepBranch(
          JCas jcas, Token token, Multimap<Token, Token> head2children, Set<Token> exclusions,
          int variantLimit) {
    List<Token> branchTokens = new ArrayList<>();
    getConstituentTokens(token, head2children, exclusions, branchTokens);
    return IntStream.range(0, Math.min(variantLimit, branchTokens.size()))
            .mapToObj(i -> branchTokens.subList(0, i + 1))
            .map(tokens -> createCandidateAnswerOccurrence(jcas, branchTokens)).collect(toList());
  }

  public static CandidateAnswerOccurrence createCandidateAnswerOccurrenceFromDepBranch(JCas jcas,
          Token token, Multimap<Token, Token> head2children, Set<Token> exclusions) {
    List<Token> branchTokens = new ArrayList<>();
    getConstituentTokens(token, head2children, exclusions, branchTokens);
    return createCandidateAnswerOccurrence(jcas, branchTokens);
  }

  private static void getConstituentTokens(Token token, Multimap<Token, Token> head2children,
          Set<Token> exclusions, List<Token> branchTokens) {
    branchTokens.add(token);
    head2children.get(token).stream()
            .filter(child -> exclusions == null || !exclusions.contains(child))
            .forEach(child -> getConstituentTokens(child, head2children, exclusions, branchTokens));
  }

  public static CandidateAnswerVariant createCandidateAnswerVariant(JCas jcas, Concept concept) {
    Collection<ConceptMention> cmentions = TypeUtil.getConceptMentions(concept);
    List<CandidateAnswerOccurrence> caos = cmentions.stream()
            .map(cmention -> TypeFactory.createCandidateAnswerOccurrence(getJCas(cmention),
                    cmention.getBegin(), cmention.getEnd()))
            .collect(toList());
    List<String> names = cmentions.stream()
            .flatMap(cmention -> Stream.of(cmention.getCoveredText(), cmention.getMatchedName()))
            .filter(Objects::nonNull).distinct().collect(toList());
    return TypeFactory.createCandidateAnswerVariant(jcas, caos, names);
  }

  public static CandidateAnswerVariant createCandidateAnswerVariant(JCas jcas, Token token) {
    CandidateAnswerOccurrence cao = TypeFactory.createCandidateAnswerOccurrence(getJCas(token),
            token.getBegin(), token.getEnd());
    return TypeFactory.createCandidateAnswerVariant(jcas, Arrays.asList(cao));
  }

  private static CandidateAnswerOccurrence createCandidateAnswerOccurrence(JCas jcas,
          Collection<Token> tokens) {
    int begin = tokens.stream().mapToInt(Token::getBegin).min().orElse(0);
    int end = tokens.stream().mapToInt(Token::getEnd).max().orElse(0);
    return TypeFactory.createCandidateAnswerOccurrence(jcas, begin, end);
  }

  public static JCas getJCas(AnnotationBaseFS annotation) {
    try {
      return annotation.getView().getJCas();
    } catch (CASException e) {
      throw new UIMARuntimeException(e);
    }
  }

  public static int getDepth(Token token) {
    int depth = 0;
    Token curToken = token;
    while ((curToken = curToken.getHead()) != null) {
      depth++;
    }
    return depth;
  }

  public static boolean isConstituentForest(JCas jcas, Collection<Token> tokens) {
    SetMultimap<Token, Token> head2children = CavUtil
            .getHeadTokenMap(TypeUtil.getOrderedTokens(jcas));
    return tokens.stream().allMatch(token -> isConstituentForest(head2children, tokens, token));
  }

  private static boolean isConstituentForest(SetMultimap<Token, Token> head2children,
          Collection<Token> coveredTokens, Token token) {
    if (!coveredTokens.contains(token)) {
      return false;
    }
    return head2children.get(token).stream()
            .allMatch(child -> isConstituentForest(head2children, coveredTokens, child));
  }

  public static boolean isConstituent(JCas jcas, Collection<Token> tokens) {
    if (!isConstituentForest(jcas, tokens)) {
      return false;
    }
    return 1 == tokens.stream().map(Token::getHead).filter(head -> !tokens.contains(head)).count();
  }

  public static List<CandidateAnswerVariant> cleanup(JCas jcas, List<CandidateAnswerVariant> cavs,
          Set<String> filteredStrings) {
    return cavs.stream().map(cav -> {
      if (TypeUtil.getCandidateAnswerVariantNames(cav).stream().anyMatch(v -> v == null))
        System.out.println(cav);
      List<String> names = TypeUtil.getCandidateAnswerVariantNames(cav).stream()
              .filter(v -> !filteredStrings.contains(v.toLowerCase())).collect(toList());
      if (names.isEmpty())
        return null;
      cav.setNames(FSCollectionFactory.createStringList(jcas, names));
      return cav;
    } ).filter(Objects::nonNull).collect(toList());
  }
  
  public static double getPathLength(Token src, Token dst, double infinity) {
    List<Token> path = getPath(src, dst);
    if (path == null) {
      return infinity;
    } else {
      return path.size();
    }
  }

  public static List<Token> getPath(Token src, Token dst) {
    if (src == null || dst == null) {
      return null;
    }
    List<Token> srcPathFromRoot = Lists.reverse(getPathToRoot(src));
    List<Token> dstPathFromRoot = Lists.reverse(getPathToRoot(dst));
    int commonLen = Math.min(srcPathFromRoot.size(), dstPathFromRoot.size());
    int firstDiffIndex = IntStream.range(0, commonLen)
            .filter(i -> !srcPathFromRoot.get(i).equals(dstPathFromRoot.get(i))).findFirst()
            .orElse(commonLen);
    // different root: src and dst may be from different sentences
    if (firstDiffIndex == 0) {
      return null;
    }
    ImmutableList.Builder<Token> builder = ImmutableList.builder();
    builder.addAll(Lists.reverse(srcPathFromRoot.subList(firstDiffIndex, srcPathFromRoot.size())));
    builder.add(srcPathFromRoot.get(firstDiffIndex - 1));
    builder.addAll(dstPathFromRoot.subList(firstDiffIndex, dstPathFromRoot.size()));
    return builder.build();
  }

  private static List<Token> getPathToRoot(Token token) {
    List<Token> path = new ArrayList<>();
    Token curToken = token;
    while ((curToken = curToken.getHead()) != null) {
      path.add(curToken);
    }
    return path;
  }

}
