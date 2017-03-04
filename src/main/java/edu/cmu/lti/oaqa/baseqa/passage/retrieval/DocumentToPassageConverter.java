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

package edu.cmu.lti.oaqa.baseqa.passage.retrieval;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import edu.emory.clir.clearnlp.tokenization.EnglishTokenizer;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * This is the simplest candidate {@link Passage} generator, which segments the document texts
 * (from {@link Document#getText()}) into sentences, and create a candidate {@link Passage} for each
 * sentence.
 * The retrieval score of each document is used as the initial score of the {@link Passage}s.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/19/14
 */
public class DocumentToPassageConverter extends JCasAnnotator_ImplBase {

  private boolean includeTitleAbstract;

  private boolean includeSections;

  private boolean onlyForDocumentsMissingSections;

  private static final Logger LOG = LoggerFactory.getLogger(DocumentToPassageConverter.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    includeTitleAbstract = UimaContextHelper.getConfigParameterBooleanValue(context,
            "include-title-abstract", true);
    includeSections = UimaContextHelper.getConfigParameterBooleanValue(context, "include-sections",
            true);
    onlyForDocumentsMissingSections = UimaContextHelper.getConfigParameterBooleanValue(context,
            "only-for-documents-missing-sections", true);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<Document> documents = TypeUtil.getRankedDocuments(jcas);
    List<Passage> passages = new ArrayList<>();
    if (includeTitleAbstract) {
      for (Document doc : documents) {
        if (onlyForDocumentsMissingSections && doc.getSections().toArray().length > 0) continue;
        if (doc.getTitle() != null)
          passages.addAll(segmentSentences(jcas, doc, doc.getTitle(), "title"));
        if (doc.getText() != null)
          passages.addAll(segmentSentences(jcas, doc, doc.getText(), "abstract"));
      }
    }
    if (includeSections) {
      for (Document doc : documents) {
        if (doc.getSections().toArray().length == 0) continue;
        String[] sections = doc.getSections().toArray();
        String[] sectionLabels = doc.getSectionLabels().toArray();
        for (int i = 0; i < sections.length; i++) {
          passages.addAll(segmentSentences(jcas, doc, sections[i], sectionLabels[i]));
        }
      }
    }
    LOG.info("Converted {} documents to {} passages.", documents.size(), passages.size());
    TypeUtil.rankedSearchResultsByScore(passages, Integer.MAX_VALUE).forEach(Passage::addToIndexes);
  }

  private static List<Passage> segmentSentences(JCas jcas, Document document, String text,
          String section) {
    return segmentSentences(jcas, document.getUri(), document.getScore(), text, document.getRank(),
            document.getDocId(), section);
  }

  private static List<Passage> segmentSentences(JCas jcas, String uri, double score, String text,
          int rank, String docId, String section) {
    EnglishTokenizer tokenizer = new EnglishTokenizer();
    List<List<String>> tokenLists = tokenizer
            .segmentize(new ByteArrayInputStream(text.getBytes(StandardCharsets.UTF_8)));
    List<Passage> passages = new ArrayList<>();
    int offset = 0;
    for (List<String> tokens : tokenLists) {
      offset = Math.max(text.indexOf(tokens.get(0), offset), offset); // in case of -1
      int sentenceBegin = offset;
      offset += tokens.get(0).length();
      for (int i = 1; i < tokens.size(); i++) {
        offset = Math.max(text.indexOf(tokens.get(i), offset), offset); // in case of -1
        offset += tokens.get(i).length();
      }
      int sentenceEnd = offset;
      String passageText = text.substring(sentenceBegin, sentenceEnd);
      passages.add(TypeFactory
              .createPassage(jcas, uri, score, passageText, rank, docId, sentenceBegin, sentenceEnd,
                      section, section));
    }
    return passages;
  }

}
