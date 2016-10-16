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

import com.aliasi.sentences.IndoEuropeanSentenceModel;
import com.aliasi.sentences.SentenceChunker;
import com.aliasi.sentences.SentenceModel;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import edu.cmu.lti.oaqa.baseqa.providers.query.BooleanBagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.passage.RetrievalUtil;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
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

import java.io.IOException;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   This is an unsupervised {@link Passage} candidate generator and reranker.
 *   It first splits each {@link Document} into {@link Passage}s at the sentence level, and then
 *   build an in-memory Lucene index for all the sentences.
 *   Relevant {@link Passage}s are retrieved by a query string, translated by a
 *   {@link QueryStringConstructor} from the {@link AbstractQuery}.
 * </p>
 * <p>
 *   A supervised version that uses the retrieval scores of the sentences as features is implemented
 *   in {@link edu.cmu.lti.oaqa.baseqa.passage.rerank.scorers.LuceneInMemoryPassageScorer}.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.passage.rerank.scorers.LuceneInMemoryPassageScorer
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/19/14
 */
public class LuceneInMemorySentenceRetrievalExecutor extends JCasAnnotator_ImplBase {

  private SentenceChunker chunker;

  private Analyzer analyzer;

  private int hits;

  private QueryParser parser;

  private QueryStringConstructor queryStringConstructor;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    // initialize sentence chunker
    TokenizerFactory tokenizerFactory = UimaContextHelper.createObjectFromConfigParameter(context,
            "tokenizer-factory", "tokenizer-factory-params", IndoEuropeanTokenizerFactory.class,
            TokenizerFactory.class);
    SentenceModel sentenceModel = UimaContextHelper.createObjectFromConfigParameter(context,
            "sentence-model", "sentence-model-params", IndoEuropeanSentenceModel.class,
            SentenceModel.class);
    chunker = new SentenceChunker(tokenizerFactory, sentenceModel);
    // initialize hits
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 200);
    // initialize query analyzer, index writer config, and query parser
    analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    parser = new QueryParser("text", analyzer);
    // initialize query string constructor
    queryStringConstructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            BooleanBagOfPhraseQueryStringConstructor.class, QueryStringConstructor.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // create lucene documents for all sentences in all sections
    Map<Integer, Passage> hash2passage = TypeUtil.getRankedPassages(jcas).stream()
            .flatMap(sec -> RetrievalUtil.extractSentences(jcas, sec, chunker).stream())
            .collect(toMap(TypeUtil::hash, Function.identity()));
    List<Document> luceneDocs = hash2passage.values().stream()
            .map(RetrievalUtil::createLuceneDocument).collect(toList());
    // create lucene index
    RAMDirectory index = new RAMDirectory();
    try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
      writer.addDocuments(luceneDocs);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // search in the index
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).stream().findFirst().get();
    Map<Integer, Float> hash2score = new HashMap<>();
    try (IndexReader reader = DirectoryReader.open(index)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      String queryString = queryStringConstructor.construct(aquery);
      System.out.println("  - Search for query: " + queryString);
      parser.createBooleanQuery("title", queryString);
      Query query = parser.parse(queryString);
      ScoreDoc[] scoreDocs = searcher.search(query, hits).scoreDocs;
      for (ScoreDoc scoreDoc : scoreDocs) {
        float score = scoreDoc.score;
        int hash;
        hash = Integer.parseInt(searcher.doc(scoreDoc.doc).get("hash"));
        hash2score.put(hash, score);
      }
    } catch (IOException | ParseException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // add to CAS
    hash2score.entrySet().stream().map(entry -> {
      Passage passage = hash2passage.get(entry.getKey());
      passage.setScore(entry.getValue());
      return passage;
    } ).sorted(Comparator.comparing(Passage::getScore).reversed()).forEach(Passage::addToIndexes);
  }

}
