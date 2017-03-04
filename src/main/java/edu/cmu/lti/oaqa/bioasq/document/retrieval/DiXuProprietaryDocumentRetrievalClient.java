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

package edu.cmu.lti.oaqa.bioasq.document.retrieval;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;

/**
 * A relevant {@link Document} retrieval service client that is used in BioASQ 3B.
 *
 * @see edu.cmu.lti.oaqa.baseqa.document.retrieval.LuceneDocumentRetrievalExecutor
 *
 * @author <a href="mailto:niloygupta@gmail.com">Niloy Gupta</a> created on 4/25/16
 */
public class DiXuProprietaryDocumentRetrievalClient extends JCasAnnotator_ImplBase {

  private String host;

  private int port;

  private String uriPrefix;

  private static final Logger LOG = LoggerFactory
          .getLogger(DiXuProprietaryDocumentRetrievalClient.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    host = UimaContextHelper.getConfigParameterStringValue(context, "host");
    port = UimaContextHelper.getConfigParameterIntValue(context, "port", 10080);
    uriPrefix = UimaContextHelper.getConfigParameterStringValue(context, "uri-prefix");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String query = TypeUtil.getQuestion(jcas).getText();
    List<Document> documents = new ArrayList<>();
    LOG.info("Attempting to connect to host {} on port {}.", host, port);
    try {
      Socket echoSocket = new Socket(host, port);
      PrintWriter out = new PrintWriter(echoSocket.getOutputStream(), true);
      BufferedReader in = new BufferedReader(new InputStreamReader(echoSocket.getInputStream()));
      out.println(query);
      String lineFromSever = in.readLine();
      JSONObject jsobj = new JSONObject(lineFromSever);
      JSONArray docnoList = (JSONArray) jsobj.get("docno_list");
      JSONArray scoreList = (JSONArray) jsobj.get("score_list");
      JSONArray titleList = (JSONArray) jsobj.get("title_list");
      JSONArray abstractList = (JSONArray) jsobj.get("abstract_list");
      int rank = 1;
      for (int i = 0; i < docnoList.length(); i++) {
        Document doc = TypeFactory.createDocument(jcas, uriPrefix + docnoList.get(i));
        doc.setScore((double) scoreList.get(i));
        doc.setRank(rank++);
        doc.setTitle((String) titleList.get(i));
        doc.setText((String) abstractList.get(i));
        documents.add(doc);
        if (rank > 10)
          break;
      }
      out.close();
      in.close();
      echoSocket.close();
    } catch (Exception e) {
      System.err.println("Don't know about host: " + host);
      throw new AnalysisEngineProcessException(e);
    }
    documents.forEach(Document::addToIndexes);
  }

}
