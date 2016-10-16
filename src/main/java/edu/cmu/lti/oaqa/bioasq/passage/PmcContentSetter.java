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

package edu.cmu.lti.oaqa.bioasq.passage;

import com.google.common.io.CharStreams;
import com.google.gson.Gson;
import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.StringArray;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.io.InputStreamReader;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * This class should be used after a document retrieval step if the document's text is not returned
 * by the {@link Document} (e.g.
 * {@link edu.cmu.lti.oaqa.bioasq.document.retrieval.GoPubMedDocumentRetrievalExecutor}), and before
 * a passage retrieval step that requires the document text.
 *
 * This class fetches a <a href="https://www.ncbi.nlm.nih.gov/pmc/">PubMed Central</a> document
 * using a HTTP service for JSON document, which can be generated using the official document
 * processing tool from <a href="http://bioasq.org/">http://bioasq.org/</a>, and then split into
 * individual JSON objects.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/19/14
 */
public class PmcContentSetter extends JCasAnnotator_ImplBase {

  private static Gson gson = new Gson();

  private String urlFormat;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    urlFormat = UimaContextHelper.getConfigParameterStringValue(context, "url-format");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    int count = 0;
    for (Document doc : TypeUtil.getRankedDocuments(jcas)) {
      URL url;
      try {
        url = new URL(String.format(urlFormat, doc.getDocId()));
      } catch (MalformedURLException e) {
        throw new AnalysisEngineProcessException(e);
      }
      String json;
      try {
        json = CharStreams.toString(new InputStreamReader(url.openStream()));
      } catch (IOException e) {
        System.out.println("Error access " + url.toString());
        throw new AnalysisEngineProcessException(e);
      }
      if (json.isEmpty()) {
        continue;
      }
      List<String> sections;
      try {
        sections = gson.fromJson(json, PmcDocument.class).getSections();
      } catch (JsonSyntaxException | JsonIOException e) {
        throw new AnalysisEngineProcessException(e);
      }
      doc.setSections((StringArray) FSCollectionFactory.createStringArray(jcas, sections));
      List<String> sectionLabels = IntStream.range(0, sections.size())
              .mapToObj(i -> "sections." + i).collect(toList());
      doc.setSectionLabels(
              (StringArray) FSCollectionFactory.createStringArray(jcas, sectionLabels));
      count++;
    }
    System.out.println("total pmc documents with content: " + count);
  }

  public static final class PmcDocument {

    private String pmid;

    private String title;

    private List<String> sections;

    public PmcDocument(String pmid, String title, List<String> sections) {
      super();
      this.pmid = pmid;
      this.title = title;
      this.sections = sections;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + ((pmid == null) ? 0 : pmid.hashCode());
      return result;
    }

    @Override
    public boolean equals(Object obj) {
      if (this == obj)
        return true;
      if (obj == null)
        return false;
      if (getClass() != obj.getClass())
        return false;
      PmcDocument other = (PmcDocument) obj;
      if (pmid == null) {
        if (other.pmid != null)
          return false;
      } else if (!pmid.equals(other.pmid))
        return false;
      return true;
    }

    public String getPmid() {
      return pmid;
    }

    public void setPmid(String pmid) {
      this.pmid = pmid;
    }

    public String getTitle() {
      return title;
    }

    public void setTitle(String title) {
      this.title = title;
    }

    public List<String> getSections() {
      return sections;
    }

    public void setSections(List<String> sections) {
      this.sections = sections;
    }

  }

}
