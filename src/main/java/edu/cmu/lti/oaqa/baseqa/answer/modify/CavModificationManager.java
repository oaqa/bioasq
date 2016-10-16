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

package edu.cmu.lti.oaqa.baseqa.answer.modify;

import static java.util.stream.Collectors.toList;

import java.util.Collection;
import java.util.List;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import edu.cmu.lti.oaqa.baseqa.answer.modify.modifiers.CavModifier;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.util.TypeUtil;

/**
 * A {@link JCasAnnotator_ImplBase} that allows to specify and integrate <tt>handlers</tt> that
 * implement the {@link CavModifier} interface.
 *
 * @see CavModifier
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class CavModificationManager extends JCasAnnotator_ImplBase {

  private List<CavModifier> modifiers;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String handlerNames = UimaContextHelper.getConfigParameterStringValue(context, "handlers");
    modifiers = ProviderCache.getProviders(handlerNames, CavModifier.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (CavModifier modifier : modifiers) {
      if (modifier.accept(jcas)) {
        modifier.modify(jcas);
        List<Collection<String>> names = TypeUtil.getCandidateAnswerVariants(jcas).stream()
                .map(TypeUtil::getCandidateAnswerVariantNames).collect(toList());
        System.out.println("Answer candidates modified: " + names + " from "
                + modifier.getClass().getSimpleName());
      }
    }
  }

}
