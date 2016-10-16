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

package edu.cmu.lti.oaqa.bioqa.providers.kb;

import com.google.common.base.Charsets;
import com.google.common.io.Files;
import com.sun.org.apache.xalan.internal.xsltc.trax.TransformerFactoryImpl;
import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import gov.nih.nlm.nls.skr.GenericObject;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.stax.StAXSource;
import javax.xml.transform.stream.StreamResult;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;

/**
 * <p>
 *   This {@link ConceptProvider} wraps <a href="https://metamap.nlm.nih.gov/">MetaMap</a>
 *   <a href="https://ii.nlm.nih.gov/Web_API/index.shtml">Web API service</a>.
 * </p>
 * <p>
 *   The following configuration is used:
 *   <pre>
 *     metamap -V USAbase -L 14 -Z VERSION -E -Av --XMLf
 *   </pre>
 *   where the <tt>VERSION</tt> can be specified in the descriptor via <tt>version</tt> parameter.
 *   In addition, as the MetaMap server will queue the jobs, you are encouraged to use the batch
 *   mode (i.e. {@link #getConcepts(List)}.
 * </p>
 *
 * @see CachedMetaMapConceptProvider
 * @see MetaMapObject
 * @see MetaMapConceptConvertUtil
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 7/12/15
 */
public class MetaMapConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private GenericObject conf;

  private XMLInputFactory xmlInputFactory;

  private Transformer transformer;

  private Unmarshaller unmarshaller;

  public MetaMapConceptProvider() {
  }

  MetaMapConceptProvider(String version, String username, String password, String email,
          boolean silentOnError, int priority) throws ResourceInitializationException {
    conf = createConf(version, username, password, email, silentOnError, priority);
    xmlInputFactory = XMLInputFactory.newFactory();
    try {
      transformer = new TransformerFactoryImpl().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      unmarshaller = JAXBContext.newInstance(MetaMapObject.class).createUnmarshaller();
    } catch (TransformerConfigurationException | JAXBException e) {
      throw new ResourceInitializationException();
    }
  }

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String version = String.class.cast(getParameterValue("version"));
    String username = String.class.cast(getParameterValue("username"));
    String password = String.class.cast(getParameterValue("password"));
    String email = String.class.cast(getParameterValue("email"));
    conf = createConf(version, username, password, email, false, 0);
    xmlInputFactory = XMLInputFactory.newFactory();
    try {
      transformer = new TransformerFactoryImpl().newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      unmarshaller = JAXBContext.newInstance(MetaMapObject.class).createUnmarshaller();
    } catch (TransformerConfigurationException | JAXBException e) {
      throw new ResourceInitializationException();
    }
    return ret;
  }

  private static GenericObject createConf(String version, String username, String password,
          String email, boolean silentOnError, int priority) {
    GenericObject conf = new GenericObject(username, password);
    conf.setField("Email_Address", email);
    conf.setField("Batch_Command", "metamap -V USAbase -L 14 -Z " + version + " -E -Av --XMLf");
    conf.setField("SilentEmail", true);
    conf.setField("ESilent", silentOnError);
    conf.setField("RPriority", Integer.toString(priority));
    return conf;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    return getConcepts(Collections.singletonList(jcas));
  }

  @Override
  public List<Concept> getConcepts(List<JCas> jcases) throws AnalysisEngineProcessException {
    List<String> texts = jcases.stream().map(JCas::getDocumentText).collect(toList());
    List<String> mmoStrings = requestConcepts(texts);
    assert texts.size() == mmoStrings.size();
    List<Concept> concepts = new ArrayList<>();
    for (int i = 0; i < jcases.size(); i++) {
      StringReader mmoStringReader = new StringReader(mmoStrings.get(i));
      try {
        MetaMapObject mmo = (MetaMapObject) unmarshaller.unmarshal(mmoStringReader);
        concepts.addAll(
                MetaMapConceptConvertUtil.convertMetaMapObjectToConcepts(jcases.get(i), mmo));
      } catch (JAXBException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    return concepts;
  }

  protected List<String> requestConcepts(List<String> texts) throws AnalysisEngineProcessException {
    File file;
    try {
      file = File.createTempFile("metamap-", ".input");
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    String lines = IntStream.range(0, texts.size()).mapToObj(texts::get)
            .map(MetaMapConceptProvider::formatBody).collect(joining("\n\n"));
    try {
      Files.write(lines, file, Charsets.UTF_8);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    conf.setFileField("UpLoad_File", file.toString());
    System.out.println("Request ready for " + texts.size() + " inputs.");
    String response = conf.handleSubmission();
    file.deleteOnExit();
    System.out.println("Response received.");
    List<String> mmoStrings;
    try {
      mmoStrings = splitResponseByMMO(response);
    } catch (Exception e) {
      System.out.println("Returned: " + response);
      throw new AnalysisEngineProcessException(e);
    }
    System.out.println("MetaMap concept provider retrieved " + mmoStrings.size() + " MMOs for "
            + texts.size() + " inputs.");
    return mmoStrings;
  }

  private List<String> splitResponseByMMO(String response)
          throws XMLStreamException, TransformerException {
    XMLStreamReader reader = xmlInputFactory.createXMLStreamReader(new StringReader(response));
    while (!reader.hasName() || !"MMOs".equals(reader.getLocalName())) {
      reader.next();
    }
    List<String> mmoStrings = new ArrayList<>();
    while (reader.nextTag() == XMLStreamConstants.START_ELEMENT) {
      StringWriter buffer = new StringWriter();
      transformer.transform(new StAXSource(reader), new StreamResult(buffer));
      mmoStrings.add(buffer.toString());
    }
    return mmoStrings;
  }

  private static String formatBody(String text) {
    return text.trim().replaceAll("\\s", " ").replaceAll("–", "-").replaceAll("’", "'")
            .replaceAll("[^\\p{ASCII}]", " ");
  }

}
