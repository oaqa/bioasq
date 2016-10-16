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

package edu.cmu.lti.oaqa.bioqa.providers.kb;

import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.jcas.JCas;

import java.util.Arrays;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * This utility class creates {@link Concept}s from {@link MetaMapObject}s returned by the
 * {@link MetaMapConceptProvider}.
 *
 * @see MetaMapObject
 * @see MetaMapConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 7/12/15.
 */
class MetaMapConceptConvertUtil {

  static List<Concept> convertMetaMapObjectToConcepts(JCas jcas, MetaMapObject mmo) {
    return mmo.getUtterances().stream().map(MetaMapObject.Utterance::getPhrases)
            .flatMap(List::stream).map(
                    MetaMapObject.Phrase::getMappings)
            .flatMap(mappings -> mappings.stream().limit(1)).map(
                    MetaMapObject.Mapping::getMappingCandidates).flatMap(List::stream).map(
                    candidate -> convertMetaMapMappingCandidateToConcept(jcas, candidate))
            .collect(toList());
  }

  private static Concept convertMetaMapMappingCandidateToConcept(JCas jcas,
          MetaMapObject.Candidate candidate) {
    double score = candidate.getCandidateScore() / -1000.0;
    String id = "UMLS:" + candidate.getCandidateCUI();
    String preferredName = candidate.getCandidatePreferred();
    String matchedName = candidate.getCandidateMatched();
    List<ConceptType> types = candidate.getSemTypes().stream()
            .map(name -> TypeFactory.createConceptType(jcas, "umls:" + name))
            .collect(toList());
    ConceptMention mention = convertMetaMapConceptPIToConceptMention(jcas,
            candidate.getConceptPIs(),
            matchedName, score);
    List<String> names = Arrays.asList(preferredName, matchedName, mention.getCoveredText());
    return TypeFactory.createConcept(jcas, names, id, mention, types);
  }

  private static ConceptMention convertMetaMapConceptPIToConceptMention(JCas jcas,
          List<MetaMapObject.ConceptPI> conceptPIs,
          String matchedName, double score) {
    char[] chars = jcas.getDocumentText().toCharArray();
    int nonWhitespaceHeadPos = IntStream.range(0, chars.length)
            .filter(i -> !Character.isWhitespace(chars[i])).findFirst().getAsInt();
    // MetaMap allows disjoint text spans, but UIMA does not. We use the tightest span instead.
    int begin = nonWhitespaceHeadPos +
            conceptPIs.stream().mapToInt(MetaMapObject.ConceptPI::getStartPos).min().orElse(0);
    int end = nonWhitespaceHeadPos +
            conceptPIs.stream().mapToInt(pi -> pi.getStartPos() + pi.getLength()).max().orElse(0);
    return TypeFactory.createConceptMention(jcas, begin, end, matchedName, score);
  }

}
