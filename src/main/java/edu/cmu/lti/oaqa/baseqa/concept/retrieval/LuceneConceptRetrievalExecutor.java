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

package edu.cmu.lti.oaqa.baseqa.concept.retrieval;

import com.google.common.io.Resources;
import edu.cmu.lti.oaqa.baseqa.providers.query.BooleanBagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.providers.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
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

import java.io.IOException;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.stream.Collectors.toMap;

/**
 * <p>
 *   A {@link JCasAnnotator_ImplBase} that performs search using a query string, transformed from a
 *   {@link AbstractQuery} by a {@link QueryStringConstructor}, on a local Lucene index for
 *   descriptions of all concepts to retrieve relevant {@link ConceptSearchResult}s.
 * </p>
 * <p>
 *   The index should contain four mandatory fields: <tt>id</tt>, <tt>name</tt>,
 *   <tt>definition</tt>, and <tt>source</tt>.
 *   Different sources of ontologies need to be adapted into the same single schema, and specify the
 *   <tt>source</tt> and <tt>id</tt> of the concept in the original ontology source.
 *   <tt>Definition</tt> and <tt>name</tt> fields are intended to be used for retrieval.
 *   An example of indexing multiple ontologies can be found
 *   <a href="https://github.com/YueChou/biomedical-concept-indexer">https://github.com/YueChou/biomedical-concept-indexer</a>.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/13/16
 */
public class LuceneConceptRetrievalExecutor extends JCasAnnotator_ImplBase {

  private QueryStringConstructor constructor;

  private int hits;

  private QueryParser parser;

  private IndexReader reader;

  private IndexSearcher searcher;

  private String idFieldName;

  private String nameFieldName;

  private String sourceFieldName;

  private Map<String, String> uriPrefix;

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
    nameFieldName = UimaContextHelper.getConfigParameterStringValue(context, "name-field", null);
    sourceFieldName = UimaContextHelper
            .getConfigParameterStringValue(context, "source-field", null);
    String uriPrefixPath = UimaContextHelper.getConfigParameterStringValue(context, "uri-prefix");
    try {
      uriPrefix = Resources.readLines(getClass().getResource(uriPrefixPath), UTF_8).stream()
              .map(line -> line.split("\t")).collect(toMap(segs -> segs[0], segs -> segs[1]));
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<AbstractQuery> aqueries = TypeUtil.getAbstractQueries(jcas);
    List<ConceptSearchResult> concepts = new ArrayList<>();
    for (AbstractQuery aquery : aqueries) {
      String queryString = constructor.construct(aquery);
      TopDocs results;
      try {
        Query query = parser.parse(queryString);
        results = searcher.search(query, hits);
      } catch (ParseException | IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
      boolean returnsNotEmpty = false;
      for (ScoreDoc scoreDoc : results.scoreDocs) {
        try {
          concepts.add(convertScoreDocToConceptSearchResult(jcas, scoreDoc, queryString));
        } catch (IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
        returnsNotEmpty = true;
      }
      if (returnsNotEmpty)
        break;
    }
    TypeUtil.rankedSearchResultsByScore(concepts, hits).forEach(ConceptSearchResult::addToIndexes);
  }

  private ConceptSearchResult convertScoreDocToConceptSearchResult(JCas jcas, ScoreDoc scoreDoc,
          String queryString) throws IOException {
    Document doc = reader.document(scoreDoc.doc);
    String source = sourceFieldName == null ? null : doc.get(sourceFieldName);
    String name = nameFieldName == null ? null : doc.get(nameFieldName);
    String uri = uriPrefix.get(source) + (idFieldName == null ? null : doc.get(idFieldName));
    Concept concept = TypeFactory.createConcept(jcas, name, uri);
    return TypeFactory
            .createConceptSearchResult(jcas, concept, uri, scoreDoc.score, name, queryString,
                    source);
  }

}
