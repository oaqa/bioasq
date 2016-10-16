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

package edu.cmu.lti.oaqa.bioasq.util;

import com.google.common.collect.ImmutableList;
import edu.cmu.lti.oaqa.bio.bioasq.services.GoPubMedService;
import edu.cmu.lti.oaqa.bio.bioasq.services.LinkedLifeDataServiceResponse;
import edu.cmu.lti.oaqa.bio.bioasq.services.OntologyServiceResponse;
import edu.cmu.lti.oaqa.bio.bioasq.services.PubMedSearchServiceResponse;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.Triple;
import edu.cmu.lti.oaqa.type.retrieval.ConceptSearchResult;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.type.retrieval.TripleSearchResult;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.jcas.JCas;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * A utility class to convert GoPubMed services ({@link GoPubMedService} I/O objects to the types.
 *
 * @see GoPubMedService
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public class BioASQUtil {

  private static final String PUMMED_URI_PREFIX = "http://www.ncbi.nlm.nih.gov/pubmed/";

  public enum Ontology {DISEASE, GENE, JOCHEM, MESH, UNIPROT};

  private static String getPubMedUri(String pmid) {
    return PUMMED_URI_PREFIX + pmid;
  }

  private synchronized static List<ConceptSearchResult> createConceptSearchResults(JCas jcas,
          OntologyServiceResponse.Result result, String searchId) {
    String queryString = result.getKeywords();
    return result.getFindings().stream().map(finding -> {
      String uri = finding.getConcept().getUri();
      double score = finding.getScore();
      String text = finding.getConcept().getLabel();
      Concept concept = TypeFactory.createConcept(jcas, text, uri);
      return TypeFactory
              .createConceptSearchResult(jcas, concept, uri, score, text, queryString, searchId);
    } ).collect(toList());
  }

  public static List<ConceptSearchResult> searchOntology(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage, Ontology ontology) throws IOException {
    switch (ontology) {
      case DISEASE: return searchDiseaseOntology(service, jcas, keywords, pages, conceptsPerPage);
      case GENE: return searchGeneOntology(service, jcas, keywords, pages, conceptsPerPage);
      case JOCHEM: return searchJochem(service, jcas, keywords, pages, conceptsPerPage);
      case MESH: return searchMesh(service, jcas, keywords, pages, conceptsPerPage);
      case UNIPROT: return searchUniprot(service, jcas, keywords, pages, conceptsPerPage);
    }
    return ImmutableList.of();
  }

  private static List<ConceptSearchResult> searchDiseaseOntology(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage) throws IOException {
    List<ConceptSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      OntologyServiceResponse.Result result = service.findDiseaseOntologyEntitiesPaged(keywords,
              page, conceptsPerPage);
      ret.addAll(createConceptSearchResults(jcas, result, Ontology.DISEASE.name()));
      if (result.getFindings().size() < conceptsPerPage) {
        break;
      }
    }
    return ret;
  }

  private static List<ConceptSearchResult> searchGeneOntology(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage) throws IOException {
    List<ConceptSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      OntologyServiceResponse.Result result = service.findGeneOntologyEntitiesPaged(keywords, page,
              conceptsPerPage);
      ret.addAll(createConceptSearchResults(jcas, result, Ontology.GENE.name()));
      if (result.getFindings().size() < conceptsPerPage) {
        break;
      }
    }
    return ret;
  }

  private static List<ConceptSearchResult> searchJochem(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage) throws IOException {
    List<ConceptSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      OntologyServiceResponse.Result result = service.findJochemEntitiesPaged(keywords, page,
              conceptsPerPage);
      ret.addAll(createConceptSearchResults(jcas, result, Ontology.JOCHEM.name()));
      if (result.getFindings().size() < conceptsPerPage) {
        break;
      }
    }
    return ret;
  }

  private static List<ConceptSearchResult> searchMesh(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage) throws IOException {
    List<ConceptSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      OntologyServiceResponse.Result result = service.findMeshEntitiesPaged(keywords, page,
              conceptsPerPage);
      ret.addAll(createConceptSearchResults(jcas, result, Ontology.MESH.name()));
      if (result.getFindings().size() < conceptsPerPage) {
        break;
      }
    }
    return ret;
  }

  private static List<ConceptSearchResult> searchUniprot(GoPubMedService service, JCas jcas,
          String keywords, int pages, int conceptsPerPage) throws IOException {
    List<ConceptSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      OntologyServiceResponse.Result result = service.findUniprotEntitiesPaged(keywords, page,
              conceptsPerPage);
      ret.addAll(createConceptSearchResults(jcas, result, Ontology.UNIPROT.name()));
      if (result.getFindings().size() < conceptsPerPage) {
        break;
      }
    }
    return ret;
  }

  private synchronized static List<Document> createDocuments(JCas jcas,
          PubMedSearchServiceResponse.Result result) {
    String queryString = result.getKeywords();
    int startRank = result.getArticlesPerPage() * result.getPage();
    List<PubMedSearchServiceResponse.Document> docs = result.getDocuments();
    return IntStream.range(0, docs.size()).mapToObj(i -> {
      PubMedSearchServiceResponse.Document document = docs.get(i);
      String text = document.getDocumentAbstract();
      int rank = startRank + i;
      String title = document.getTitle();
      String docId = document.getPmid();
      String uri = getPubMedUri(docId);
      return TypeFactory.createDocument(jcas, uri, text, rank, queryString, title, docId);
    } ).collect(toList());
  }

  public static List<Document> searchPubMed(GoPubMedService service, JCas jcas, String keywords,
          int pages, int articlesPerPage) throws IOException {
    List<Document> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      PubMedSearchServiceResponse.Result result = service.findPubMedCitations(keywords, page,
              articlesPerPage);
      ret.addAll(createDocuments(jcas, result));
      if (result.getDocuments().size() < articlesPerPage) {
        break;
      }
    }
    return ret;
  }

  private synchronized static List<TripleSearchResult> createTripleSearchResults(JCas jcas,
          LinkedLifeDataServiceResponse.Result result) {
    String queryString = result.getQuery();
    return result.getEntities().stream().flatMap(entity ->
            entity.getRelations().stream().map(relation -> {
              String subjUri = Objects.toString(relation.getSubj(), entity.getEntity());
              String predUri = relation.getPred();
              String objUri = Objects.toString(relation.getObj(), entity.getEntity());
              Triple triple = TypeFactory.createTriple(jcas, subjUri, predUri, objUri);
              return TypeFactory.createTripleSearchResult(jcas, triple, queryString);
            })
    ).collect(toList());
  }

  public static List<TripleSearchResult> searchLinkedLifeData(GoPubMedService service, JCas jcas,
          String keywords, int pages, int entitiesPerPage) throws IOException {
    List<TripleSearchResult> ret = new ArrayList<>();
    for (int page = 0; page < pages; page++) {
      LinkedLifeDataServiceResponse.Result result = service
              .findLinkedLifeDataEntitiesPaged(keywords, page, entitiesPerPage);
      ret.addAll(createTripleSearchResults(jcas, result));
      if (result.getEntities().size() < entitiesPerPage) {
        break;
      }
    }
    return ret;
  }

}
