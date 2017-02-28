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

package edu.cmu.lti.oaqa.baseqa.document.rerank.scorers;

import com.google.common.collect.*;
import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.learning_base.AbstractScorer;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.*;

/**
 * <p>
 *   An instance of an {@link AbstractScorer} for {@link Document}s that attempts to retrieve the
 *   relevant {@link Document} from a local Lucene index, by using various different ways of
 *   constructing the queries, from only the tokens to a full combination of tokens and concepts
 *   with all the synonyms.
 *   Each query construction method contributes to a dimension of the feature, and the retrieval
 *   scores are used as the values.
 * </p>
 * <p>
 *   The index should contain three mandatory fields: <tt>id</tt>, <tt>abstractText</tt>, and
 *   <tt>articleTitle</tt>.
 *   An example of indexing a document collection can be found
 *   <a href="https://github.com/ziy/medline-indexer">https://github.com/ziy/medline-indexer</a>.
 * </p>
 *
 * @see edu.cmu.lti.oaqa.baseqa.document.retrieval.LuceneDocumentRetrievalExecutor
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/5/16
 */
public class LuceneDocumentScorer extends AbstractScorer<Document> {

  // TODO: Moved to separate files
  private final static Set<String> FORBIDDEN_CTYPES = ImmutableSet
          .of("lingpipe:other_name", "umls:qlco", "umls:qnco", "umls:ftcn", "umls:geoa");

  private int hits;

  private Set<String> stoplist;

  private IndexReader reader;

  private IndexSearcher searcher;

  private StandardAnalyzer analyzer;

  private String idFieldName;

  private String[] fields;

  private String uriPrefix;

  private Table<String, String, Double> uri2conf2score;

  private Table<String, String, Integer> uri2conf2rank;

  private Set<String> confs;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    super.initialize(aSpecifier, aAdditionalParams);
    hits = Integer.class.cast(getParameterValue("hits"));
    // query constructor
    String stoplistPath = String.class.cast(getParameterValue("stoplist-path"));
    try {
      stoplist = Resources.readLines(getClass().getResource(stoplistPath), UTF_8).stream()
              .map(String::trim).collect(toSet());
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    // load index parameters
    idFieldName = String.class.cast(getParameterValue("id-field"));
    //noinspection unchecked
    fields = Iterables.toArray((Iterable<String>) getParameterValue("fields"), String.class);
    uriPrefix = String.class.cast(getParameterValue("uri-prefix"));
    String index = String.class.cast(getParameterValue("index"));
    // create lucene
    analyzer = new StandardAnalyzer();
    try {
      reader = DirectoryReader.open(NIOFSDirectory.open(Paths.get(index)));
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    searcher = new IndexSearcher(reader);
    return true;
  }

  private static String normalizeQuoteName(String name) {
    return "\"" + QueryParser.escape(name) + "\"";
  }

  @Override
  public void prepare(JCas jcas) throws AnalysisEngineProcessException {
    uri2conf2score = HashBasedTable.create();
    uri2conf2rank = HashBasedTable.create();
    List<String> tokens = TypeUtil.getOrderedTokens(jcas).stream().map(Token::getCoveredText)
            .map(QueryParser::escape)
            .filter(name -> !name.isEmpty() && !stoplist.contains(name.toLowerCase()))
            .collect(toList());
    Multimap<String, String> ctype2names = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      Set<String> ctypes = TypeUtil.getConceptTypes(concept).stream()
              .map(ConceptType::getAbbreviation).collect(toSet());
      String cnames = TypeUtil.getConceptNames(concept).stream()
              .map(LuceneDocumentScorer::normalizeQuoteName).distinct()
              .collect(joining(" "));
      ctypes.stream().filter(t -> !FORBIDDEN_CTYPES.contains(t))
              .forEach(ctype -> ctype2names.put(ctype, cnames));
    }
    Multimap<String, String> ctypepre2names = HashMultimap.create();
    ctype2names.asMap().entrySet().stream()
            .forEach(e -> ctypepre2names.putAll(e.getKey().split(":")[0], e.getValue()));
    Multimap<String, String> ctype2mentions = HashMultimap.create();
    for (Concept concept : TypeUtil.getConcepts(jcas)) {
      Set<String> ctypes = TypeUtil.getConceptTypes(concept).stream()
              .map(ConceptType::getAbbreviation).collect(toSet());
      String cmentions = TypeUtil.getConceptMentions(concept).stream()
              .map(ConceptMention::getMatchedName)
              .map(LuceneDocumentScorer::normalizeQuoteName).distinct()
              .collect(joining(" "));
      ctypes.stream().filter(t -> !FORBIDDEN_CTYPES.contains(t))
              .forEach(ctype -> ctype2mentions.put(ctype, cmentions));
    }
    Multimap<String, String> ctypepre2mentions = HashMultimap.create();
    ctypepre2mentions.asMap().entrySet().stream()
            .forEach(e -> ctypepre2mentions.putAll(e.getKey().split(":")[0], e.getValue()));
    System.out.println("Query Strings");
    ExecutorService service = Executors.newCachedThreadPool();
    // execute against all tokens
    service.submit(() -> {
      String concatTokens = String.join(" ", tokens);
      System.out.println(" - Concatenated tokens: " + concatTokens);
      for (String field : fields) {
        searchInField(concatTokens, field, "tokens_concatenated@" + field);
      }
      searchAllField(concatTokens, "tokens_concatenated@all");
    });
    // execute against concatenated concept names
    service.submit(() -> {
      String concatCnames = String.join(" ", ctype2names.values());
      System.out.println(" - Concatenated concept names: " + concatCnames);
      for (String field : fields) {
        searchInField(concatCnames, field, "cnames_concatenated@" + field);
      }
      searchAllField(concatCnames, "cnames_concatenated@all");
    });
    // execute against concatenated concept mentions
    service.submit(() -> {
      String concatCmentions = String.join(" ", ctype2mentions.values());
      System.out.println(" - Concatenated concept mentions: " + concatCmentions);
      for (String field : fields) {
        searchInField(concatCmentions, field, "cmentions_concatenated@" + field);
      }
      searchAllField(concatCmentions, "cmentions_concatenated@");
    });
    // execute against concept names for each concept
    service.submit(() -> {
      for (String cnames : ImmutableSet.copyOf(ctype2names.values())) {
        System.out.println(" - Concatenated concept names: " + cnames);
        for (String field : fields) {
          searchInField(cnames, field, "cnames_individual@" + field);
        }
        searchAllField(cnames, "cnames_individual@all");
      }
    });
    // execute against concept names for each concept type
    service.submit(() -> {
      for (String ctype : ctype2names.keySet()) {
        String concatCnames = String.join(" ", ctype2names.get(ctype));
        System.out.println(" - Concatenated concept names for " + ctype + ": " + concatCnames);
        for (String field : fields) {
          searchInField(concatCnames, field, "cnames@" + ctype + "@" + field);
        }
        searchAllField(concatCnames, "cnames@" + ctype + "@all");
      }
    });
    // execute against concept names for each concept type prefix
    service.submit(() -> {
      for (String ctypepre : ctypepre2names.keySet()) {
        String concatCnames = String.join(" ", ctypepre2names.get(ctypepre));
        System.out.println(" - Concatenated concept names for " + ctypepre + ": " + concatCnames);
        for (String field : fields) {
          searchInField(concatCnames, field, "cnames@" + ctypepre + "@" + field);
        }
        searchAllField(concatCnames, "cnames@" + ctypepre + "@all");
      }
    });
    // execute against concept mentions for each concept
    service.submit(() -> {
      for (String cmentions : ImmutableSet.copyOf(ctype2mentions.values())) {
        System.out.println(" - Concatenated concept mentions: " + cmentions);
        for (String field : fields) {
          searchInField(cmentions, field, "cmentions_individual@" + field);
        }
        searchAllField(cmentions, "cmentions_individual@all");
      }
    });
    // execute against concept mentions for each concept type
    service.submit(() -> {
      for (String ctype : ctype2mentions.keySet()) {
        String concatCmentions = String.join(" ", ctype2mentions.get(ctype));
        System.out
                .println(" - Concatenated concept mentions for " + ctype + ": " + concatCmentions);
        for (String field : fields) {
          searchInField(concatCmentions, field, "cmentions@" + ctype + "@" + field);
        }
        searchAllField(concatCmentions, "cmentions@" + ctype + "@all");
      }
    });
    // execute against concept mentions for each concept type prefix
    service.submit(() -> {
      for (String ctypepre : ctypepre2mentions.keySet()) {
        String concatCmentions = String.join(" ", ctypepre2mentions.get(ctypepre));
        System.out.println(
                " - Concatenated concept mentions for " + ctypepre + ": " + concatCmentions);
        for (String field : fields) {
          searchInField(concatCmentions, field, "cmentions@" + ctypepre + "@" + field);
        }
        searchAllField(concatCmentions, "cmentions@" + ctypepre + "@all");
      }
    });
    service.shutdown();
    try {
      service.awaitTermination(1, TimeUnit.MINUTES);
    } catch (InterruptedException e) {
      throw new AnalysisEngineProcessException(e);
    }
    confs = new HashSet<>(uri2conf2score.columnKeySet()); // to avoid ConcurrentModificationException
  }

  private void searchInField(String queryString, String field, String conf)
          throws RuntimeException {
    if (queryString.trim().isEmpty()) return;
    search(field + ":(" + queryString + ")", conf);
  }

  private void searchAllField(String queryString, String conf) throws RuntimeException {
    if (queryString.trim().isEmpty()) return;
    String multiFieldQueryString = Arrays.stream(fields)
            .map(field -> field + ":(" + queryString + ")").collect(joining(" "));
    search(multiFieldQueryString, conf);
  }

  private void search(String queryString, String conf) throws RuntimeException {
    if (queryString.trim().isEmpty()) return;
    ScoreDoc[] results;
    try {
      QueryParser parser = new MultiFieldQueryParser(fields, analyzer);
      Query query = parser.parse(queryString);
      results = searcher.search(query, hits).scoreDocs;
    } catch (ParseException | IOException e) {
      throw new RuntimeException(e);
    }
    for (int i = 0; i < results.length; i++) {
      try {
        int doc = results[i].doc;
        String uri = uriPrefix + reader.document(doc).get(idFieldName);
        if (!uri2conf2rank.contains(uri, conf) || uri2conf2rank.get(uri, conf) > i) {
          synchronizedPut(uri2conf2rank, uri, conf, i);
        }
        double score = results[i].score;
        if (!uri2conf2score.contains(uri, conf) || uri2conf2score.get(uri, conf) < score) {
          synchronizedPut(uri2conf2score, uri, conf, score);
        }
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  private static synchronized <T extends Number> void synchronizedPut(
          Table<String, String, T> table, String uri, String conf, T value) {
    table.put(uri, conf, value);
  }

  @Override
  public Map<String, Double> score(JCas jcas, Document result) {
    ImmutableMap.Builder<String, Double> ret = new ImmutableMap.Builder<>();
    String uri = result.getUri();
    for (String conf : confs) {
      double rank = uri2conf2rank.contains(uri, conf) ?
              1.0 / (uri2conf2rank.get(uri, conf) + 1.0) :
              0.0;
      ret.put(conf + "/rank", rank);
      double score = uri2conf2score.contains(uri, conf) ?
              uri2conf2score.get(uri, conf) :
              0.0;
      ret.put(conf + "/score", score);
    }
    return ret.build();
  }

}
