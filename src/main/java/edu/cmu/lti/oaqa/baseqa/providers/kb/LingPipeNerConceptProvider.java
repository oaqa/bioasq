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

package edu.cmu.lti.oaqa.baseqa.providers.kb;

import com.aliasi.chunk.Chunker;
import com.aliasi.chunk.Chunking;
import com.aliasi.util.Streams;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.util.TypeFactory;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import java.io.ObjectInputStream;
import java.util.List;
import java.util.Map;

import static java.util.stream.Collectors.toList;

/**
 * A {@link ConceptProvider} that wraps <a href="alias-i.com/lingpipe/">LingPipe</a> NER.
 * One should specify the path to the chunker model in the descriptor via parameter
 * <tt>chunker-model</tt>.
 *
 * <p>
 *   NOTE: LingPipe has its own special
 *   <a href="http://alias-i.com/lingpipe/web/licensing.html">license</a>.
 * </p>
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/8/14
 */
public class LingPipeNerConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private Chunker chunker;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String model = String.class.cast(getParameterValue("chunker-model"));
    try (ObjectInputStream ois = new ObjectInputStream(getClass().getResourceAsStream(model))) {
      chunker = (Chunker) ois.readObject();
      Streams.closeQuietly(ois);
    } catch (Exception e) {
      throw new ResourceInitializationException(e);
    }
    return ret;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    String text = jcas.getDocumentText();
    Chunking chunking = chunker.chunk(text);
    return chunking.chunkSet().stream().map(chunk -> {
      return TypeFactory.createConcept(jcas,
              TypeFactory.createConceptMention(jcas, chunk.start(), chunk.end()),
              TypeFactory.createConceptType(jcas, "lingpipe:" + chunk.type()));
    } ).collect(toList());
  }

}
