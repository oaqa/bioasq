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

package edu.cmu.lti.oaqa.baseqa.document.retrieval;

import edu.cmu.lti.oaqa.baseqa.providers.query.BooleanBagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;

/**
 * <p>
 *   A {@link JCasAnnotator_ImplBase} that performs search using a query string, transformed from a
 *   {@link AbstractQuery} by a {@link QueryStringConstructor}, on a local Lucene index of documents
 *   to retrieve relevant {@link Document}s.
 * </p>
 * <p>
 *   The index should contain three mandatory fields: <tt>id</tt>, <tt>abstractText</tt>, and
 *   <tt>articleTitle</tt>.
 *   An example of indexing a document collection can be found
 *   <a href="https://github.com/ziy/medline-indexer">https://github.com/ziy/medline-indexer</a>.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.document.rerank.scorers.LuceneDocumentScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 7/6/15
 */
public class LuceneDocumentRetrievalExecutor extends JCasAnnotator_ImplBase {

  private QueryStringConstructor constructor;

  private int hits;

  private QueryParser parser;

  private IndexReader reader;

  private IndexSearcher searcher;

  private String idFieldName;

  private String titleFieldName;

  private String textFieldName;

  private String uriPrefix;

  private static final Logger LOG = LoggerFactory.getLogger(LuceneDocumentRetrievalExecutor.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 100);
    // query constructor
    constructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            BooleanBagOfPhraseQueryStringConstructor.class, QueryStringConstructor.class);
    // lucene
    Analyzer analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    String[] fields = UimaContextHelper.getConfigParameterStringArrayValue(context, "fields");
    parser = new MultiFieldQueryParser(fields, analyzer);
    String index = UimaContextHelper.getConfigParameterStringValue(context, "index");
    try {
      reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    searcher = new IndexSearcher(reader);
    idFieldName = UimaContextHelper.getConfigParameterStringValue(context, "id-field", null);
    titleFieldName = UimaContextHelper.getConfigParameterStringValue(context, "title-field", null);
    textFieldName = UimaContextHelper.getConfigParameterStringValue(context, "text-field", null);
    uriPrefix = UimaContextHelper.getConfigParameterStringValue(context, "uri-prefix", null);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<AbstractQuery> aqueries = TypeUtil.getAbstractQueries(jcas);
    for (AbstractQuery aquery : aqueries) {
      String queryString = constructor.construct(aquery);
      LOG.info("Query string: {}", queryString);
      TopDocs results;
      try {
        Query query = parser.parse(queryString);
        results = searcher.search(query, hits);
      } catch (ParseException | IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
      boolean returnsNotEmpty = false;
      ScoreDoc[] scoreDocs = results.scoreDocs;
      LOG.info("Retrieved {} documents.", scoreDocs.length);
      for (int i = 0; i < scoreDocs.length; i++) {
        try {
          convertScoreDocToDocument(jcas, scoreDocs[i], i, queryString).addToIndexes();
        } catch (IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
        returnsNotEmpty = true;
      }
      if (returnsNotEmpty) {
        break;
      }
    }
  }

  private Document convertScoreDocToDocument(JCas jcas, ScoreDoc scoreDoc, int rank,
          String queryString) throws IOException {
    org.apache.lucene.document.Document doc = reader.document(scoreDoc.doc);
    String id = idFieldName == null ? null : doc.get(idFieldName);
    String title = titleFieldName == null ? null : doc.get(titleFieldName);
    String text = textFieldName == null ? null : doc.get(textFieldName);
    return TypeFactory
            .createDocument(jcas, uriPrefix + id, scoreDoc.score, text, rank, queryString, title,
                    id);
  }

}
