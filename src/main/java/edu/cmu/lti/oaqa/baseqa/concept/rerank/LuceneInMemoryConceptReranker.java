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

package edu.cmu.lti.oaqa.baseqa.concept.rerank;

import edu.cmu.lti.oaqa.baseqa.providers.query.LuceneQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.RAMDirectory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   This {@link JCasAnnotator_ImplBase} provides an unsupervised method to rerank the pre-retrieved
 *   {@link ConceptSearchResult}s, building a Lucene index of only the text descriptions of the
 *   candidate {@link ConceptSearchResult}s on the fly and use (possibly different) query to select
 *   the {@link ConceptSearchResult}s.
 * </p>
 * <p>
 *   {@link edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers.LuceneConceptScorer} is a more complex
 *   implementation of Lucene based reranking that allows multiple queries and search result lists,
 *   to form a vector of scores.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.concept.rerank.scorers.LuceneConceptScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class LuceneInMemoryConceptReranker extends JCasAnnotator_ImplBase {

  private int hits;

  private QueryStringConstructor queryStringConstructor;

  private Analyzer analyzer;

  private QueryParser parser;

  private int limit;

  private float weight;

  private static final Logger LOG = LoggerFactory.getLogger(LuceneInMemoryConceptReranker.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 100);
    analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    queryStringConstructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            LuceneQueryStringConstructor.class, QueryStringConstructor.class);
    limit = UimaContextHelper.getConfigParameterIntValue(context, "limit", 10);
    weight = UimaContextHelper.getConfigParameterFloatValue(context, "rerank-weight", 1F);
    parser = new QueryParser("text", analyzer);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<ConceptSearchResult> results = TypeUtil.getRankedConceptSearchResults(jcas);
    // calculate field scores
    Map<String, ConceptSearchResult> uri2result = results.stream().collect(
            toMap(ConceptSearchResult::getUri, Function.identity(),
                    (r1, r2) -> r1.getScore() > r2.getScore() ? r1 : r2));
    List<Document> luceneDocs = results.stream()
            .map(LuceneInMemoryConceptReranker::toLuceneDocument).collect(toList());
    RAMDirectory index = new RAMDirectory();
    try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
      writer.addDocuments(luceneDocs);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).iterator().next();
    String queryString = queryStringConstructor.construct(aquery);
    LOG.info("Query string: {}", queryString);
    Map<String, Float> uri2score = new HashMap<>();
    try (IndexReader reader = DirectoryReader.open(index)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      Query query = parser.parse(queryString);
      ScoreDoc[] scoreDocs = searcher.search(query, hits).scoreDocs;
      for (ScoreDoc scoreDoc : scoreDocs) {
        uri2score.put(searcher.doc(scoreDoc.doc).get("uri"), scoreDoc.score);
      }
    } catch (IOException | ParseException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // calculate score
    for (Map.Entry<String, ConceptSearchResult> entry : uri2result.entrySet()) {
      String uri = entry.getKey();
      ConceptSearchResult result = entry.getValue();
      double score = uri2score.getOrDefault(uri, 0F) * weight + result.getScore();
      result.setScore(score);
    }
    TypeUtil.rankedSearchResultsByScore(results, limit);
    LOG.info("Reranked {} concepts.", uri2score.size());
    if (LOG.isDebugEnabled()) {
      results.stream().sorted(TypeUtil.SEARCH_RESULT_RANK_COMPARATOR).limit(20)
              .map(TypeUtil::toString).forEachOrdered(s -> LOG.debug(" - {}", s));
    }
  }

  private static Document toLuceneDocument(ConceptSearchResult result) {
    Document entry = new Document();
    entry.add(new StoredField("uri", result.getUri()));
    String names = String.join(", ", TypeUtil.getConceptNames(result.getConcept()));
    entry.add(new TextField("text", names, Field.Store.NO));
    return entry;
  }

}
