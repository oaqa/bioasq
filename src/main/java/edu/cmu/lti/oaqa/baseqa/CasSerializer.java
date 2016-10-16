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

package edu.cmu.lti.oaqa.baseqa;

import java.io.File;
import java.io.IOException;

import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.fit.util.CasIOUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.util.CasCopier;

import com.google.common.base.Strings;

import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.ecd.phase.ProcessingStepUtils;

/**
 * This utility {@link JCasAnnotator_ImplBase} serializes the {@link JCas} into a <tt>xmi</tt>
 * file, whose directory can be specified in the descriptor via the parameter <tt>dir</tt>.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/21/15
 */
public class CasSerializer extends JCasAnnotator_ImplBase {

  private String typesystem;

  private String dir;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    typesystem = UimaContextHelper.getConfigParameterStringValue(context, "typesystem");
    dir = UimaContextHelper.getConfigParameterStringValue(context, "dir");
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    try {
      JCas copied = JCasFactory.createJCas(typesystem);
      CasCopier.copyCas(jcas.getCas(), copied.getCas(), true, true);
      String id = Strings.padStart(ProcessingStepUtils.getSequenceId(jcas), 4, '0');
      CasIOUtil.writeXmi(copied, new File(dir, id + ".xmi"));
    } catch (IOException | UIMAException e) {
      e.printStackTrace();
    }
  }

}
