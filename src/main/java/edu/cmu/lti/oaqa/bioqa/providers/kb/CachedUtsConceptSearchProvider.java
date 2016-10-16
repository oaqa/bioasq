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

import com.google.common.collect.ImmutableMap;
import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptSearchProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.CustomResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.impl.CustomResourceSpecifier_impl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import java.io.File;
import java.util.*;

/**
 * A {@link UtsConceptSearchProvider} that uses a local <a href="http://www.mapdb.org/">MapDB</a>
 * instance to cache the service requests and responses.
 *
 * TODO: Created a cache provider and cacheable interface to standardize the cacheable versions of
 * services.
 *
 * @see UtsConceptSearchProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/27/16
 */
public class CachedUtsConceptSearchProvider extends ConfigurableProvider
        implements ConceptSearchProvider {

  private UtsConceptSearchProvider delegate;

  private static final Class<UtsConceptSearchProvider> delegateClass = UtsConceptSearchProvider.class;

  private DB db;

  private HTreeMap<String, Map<String, Object>> string2concept;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // initialize delegate
    CustomResourceSpecifier delegateResourceSpecifier = new CustomResourceSpecifier_impl();
    delegateResourceSpecifier.setResourceClassName(delegateClass.getCanonicalName());
    delegate = delegateClass.cast(UIMAFramework.produceResource(delegateClass,
            delegateResourceSpecifier, aAdditionalParams));
    // initialize mapdb
    File file = new File((String) getParameterValue("db-file"));
    db = DBMaker.newFileDB(file).compressionEnable().commitFileSyncDisable().cacheSize(128)
            .closeOnJvmShutdown().make();
    String map = (String) getParameterValue("map-name");
    string2concept = db.getHashMap(map);
    return ret;
  }

  @Override
  public Optional<Concept> search(String string) throws AnalysisEngineProcessException {
    return delegate.search(string);
  }

  @Override
  public Optional<Concept> search(JCas jcas, String string) throws AnalysisEngineProcessException {
    if (string2concept.containsKey(string))
      return Optional.ofNullable(toConcept(jcas, string2concept.get(string)));
    Optional<Concept> concept = delegate.search(jcas, string);
    Map<String, Object> data = fromConcept(concept.orElse(null));
    string2concept.put(string, data);
    return concept;
  }

  // TODO change to serializable object
  private static Concept toConcept(JCas jcas, Map<String, Object> data) {
    if (data.isEmpty()) return null;
    List<ConceptType> types = new ArrayList<>();
    for (String[] type : (List<String[]>) data.get("types")) {
      types.add(TypeFactory.createConceptType(jcas, type[0], type[1], type[2]));
    }
    return TypeFactory
            .createConcept(jcas, (String) data.get("name"), (String) data.get("id"), types);
  }

  private static Map<String, Object> fromConcept(Concept concept) {
    if (concept == null) return ImmutableMap.of();
    Map<String, Object> data = new HashMap<>();
    data.put("name", TypeUtil.getConceptPreferredName(concept));
    data.put("id", TypeUtil.getConceptIds(concept).stream().findFirst().orElse(null));
    List<String[]> types = new ArrayList<>();
    for (ConceptType type : TypeUtil.getConceptTypes(concept)) {
      types.add(new String[] {type.getId(), type.getName(), type.getAbbreviation()});
    }
    data.put("types", types);
    return data;
  }

  @Override
  public Optional<Concept> search(JCas jcas, String string, String searchType)
          throws AnalysisEngineProcessException {
    return delegate.search(jcas, string, searchType);
  }

  @Override
  public List<Concept> search(JCas jcas, String string, String searchType, int hits)
          throws AnalysisEngineProcessException {
    return delegate.search(jcas, string, searchType, hits);
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
