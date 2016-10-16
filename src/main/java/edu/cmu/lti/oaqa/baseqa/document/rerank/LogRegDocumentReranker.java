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

package edu.cmu.lti.oaqa.baseqa.document.rerank;

import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.providers.query.LuceneQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.RAMDirectory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   A {@link Document} reranker that uses a pretrained set of weights for different fields of each
 *   candidate, such as "title" and "body text", to rerank the {@link Document}s.
 *   The weight file can be specified via the parameter <tt>doc-logreg-params</tt>.
 * </p>
 * <p>
 *   A more general {@link Document} reranker training and prediction can be achieved from
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierTrainer} and
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.ClassifierPredictor}, with
 *   {@link edu.cmu.lti.oaqa.baseqa.learning_base.Scorer} of {@link Document} integrated.
 * </p>
 *
 * @author <a href="mailto:niloygupta@gmail.com">Niloy Gupta</a>,
 * <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/25/16
 */
public class LogRegDocumentReranker extends JCasAnnotator_ImplBase {

  private int hits;

  private QueryStringConstructor queryStringConstructor;

  private Analyzer analyzer;

  private QueryParser parser;

  private double[] docFeatWeights;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 100);
    analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    queryStringConstructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            LuceneQueryStringConstructor.class, QueryStringConstructor.class);
    parser = new QueryParser("text", analyzer);
    // load parameters
    String param = UimaContextHelper.getConfigParameterStringValue(context, "doc-logreg-params");
    try {
      docFeatWeights = Resources.readLines(getClass().getResource(param), UTF_8).stream().limit(1)
              .map(line -> line.split("\t")).flatMap(Arrays::stream)
              .mapToDouble(Double::parseDouble).toArray();
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
        /*
		 * ("arthritis"[MeSH Terms] OR "arthritis"[All Fields])
		 *  AND common[All Fields] AND ("men"[MeSH Terms] OR "men"[All Fields])) OR ("women"[MeSH Terms] OR "women"[All Fields])
		 */
    // calculate field scores
    List<Document> documents = TypeUtil.getRankedDocuments(jcas);
    Map<String, Document> id2doc = documents.stream()
            .collect(toMap(Document::getDocId, Function.identity()));
    List<org.apache.lucene.document.Document> luceneDocs = documents.stream()
            .map(LogRegDocumentReranker::toLuceneDocument).collect(toList());
    RAMDirectory index = new RAMDirectory();
    try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
      writer.addDocuments(luceneDocs);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).iterator().next();
    String queryString = queryStringConstructor.construct(aquery);
    System.out.println("  - Search for query: " + queryString);
    Map<String, Float> id2titleScore = new HashMap<>();
    Map<String, Float> id2textScore = new HashMap<>();
    try (IndexReader reader = DirectoryReader.open(index)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      searcher.setSimilarity(new BM25Similarity());
      Query titleQuery = parser.createBooleanQuery("title", queryString);
      ScoreDoc[] titleScoreDocs = searcher.search(titleQuery, hits).scoreDocs;
      System.out.println("  - Title matches: " + titleScoreDocs.length);
      for (ScoreDoc titleScoreDoc : titleScoreDocs) {
        id2titleScore.put(searcher.doc(titleScoreDoc.doc).get("id"), titleScoreDoc.score);
      }
      Query textQuery = parser.createBooleanQuery("text", queryString);
      ScoreDoc[] textScoreDocs = searcher.search(textQuery, hits).scoreDocs;
      System.out.println("  - Text matches: " + textScoreDocs.length);
      for (ScoreDoc textScoreDoc : textScoreDocs) {
        id2textScore.put(searcher.doc(textScoreDoc.doc).get("id"), textScoreDoc.score);
      }
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // set score
    for (Map.Entry<String, Document> entry : id2doc.entrySet()) {
      String id = entry.getKey();
      Document doc = entry.getValue();
      doc.setScore(calculateScore(doc.getRank(), id2titleScore.getOrDefault(id, 0f),
              id2textScore.getOrDefault(id, 0f)));
    }
    TypeUtil.rankedSearchResultsByScore(documents, hits);
  }

  private static org.apache.lucene.document.Document toLuceneDocument(Document doc) {
    org.apache.lucene.document.Document entry = new org.apache.lucene.document.Document();
    entry.add(new StoredField("id", doc.getDocId()));
    entry.add(new TextField("title", doc.getTitle(), Field.Store.NO));
    entry.add(new TextField("text", doc.getText(), Field.Store.NO));
    return entry;
  }

  private double calculateScore(int rank, float titleScore, float textScore) {
    double score =
            docFeatWeights[0] + (rank + 1) * docFeatWeights[1] + titleScore * docFeatWeights[2] +
                    textScore * docFeatWeights[3];
    double expScore = Math.exp(score);
    return expScore / (1.0 + expScore);
  }

}
