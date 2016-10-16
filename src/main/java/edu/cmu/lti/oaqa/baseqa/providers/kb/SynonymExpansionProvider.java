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

import java.util.Collection;
import java.util.Map;
import java.util.Set;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.Resource;

/**
 * An implementation of this interface can identify the list of synonyms for a given concept ID.
 * It should also override the {@link #accept(String)} method to filter the ID that is supported
 * by the particular synonym expansion provider.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/20/15
 */
public interface SynonymExpansionProvider extends Resource {

  boolean accept(String id);

  Set<String> getSynonyms(String id) throws AnalysisEngineProcessException;

  Map<String, Set<String>> getSynonyms(Collection<String> ids)
          throws AnalysisEngineProcessException;

}
