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

package edu.cmu.lti.oaqa.baseqa.answer.generate;

import edu.cmu.lti.oaqa.baseqa.answer.generate.generators.CavGenerator;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;

import static java.util.stream.Collectors.toList;

/**
 * A {@link JCasAnnotator_ImplBase} that integrates individual pluggable {@link CavGenerator}s as
 * providers.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/15
 */
public class CavGenerationManager extends JCasAnnotator_ImplBase {

  private List<CavGenerator> generators;

  private static final Logger LOG = LoggerFactory.getLogger(CavGenerationManager.class);

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String generatorNames = UimaContextHelper.getConfigParameterStringValue(context, "generators");
    generators = ProviderCache.getProviders(generatorNames, CavGenerator.class);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (CavGenerator generator : generators) {
      if (generator.accept(jcas)) {
        List<CandidateAnswerVariant> cavs = generator.generate(jcas);
        cavs.forEach(CandidateAnswerVariant::addToIndexes);
        cavs.stream().map(TypeUtil::getCandidateAnswerOccurrences).flatMap(Collection::stream)
                .forEach(CandidateAnswerOccurrence::addToIndexes);
        List<Collection<String>> names = cavs.stream().map(TypeUtil::getCandidateAnswerVariantNames)
                .collect(toList());
        LOG.info("Answer candidates generated {} from {}", names,
                generator.getClass().getSimpleName());
      }
    }
  }

}
