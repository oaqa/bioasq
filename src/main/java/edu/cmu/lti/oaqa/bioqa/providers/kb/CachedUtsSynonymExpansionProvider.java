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

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;
import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.CustomResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.impl.CustomResourceSpecifier_impl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A {@link UtsSynonymExpansionProvider} that uses a local <a href="http://www.mapdb.org/">MapDB</a>
 * instance to cache the service requests and responses.
 *
 * TODO: Created a cache provider and cacheable interface to standardize the cacheable versions of
 * services.
 *
 * @see UtsSynonymExpansionProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/20/15
 */
public class CachedUtsSynonymExpansionProvider extends ConfigurableProvider
        implements SynonymExpansionProvider {

  private UtsSynonymExpansionProvider delegate;

  private static final Class<UtsSynonymExpansionProvider> delegateClass = UtsSynonymExpansionProvider.class;

  private DB db;

  private HTreeMap<String, Set<String>> id2synonyms;

  private static final Logger LOG = LoggerFactory
          .getLogger(CachedUtsSynonymExpansionProvider.class);

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
    id2synonyms = db.getHashMap(map);
    return ret;
  }

  @Override
  public boolean accept(String id) {
    return delegate.accept(id);
  }

  @Override
  public Set<String> getSynonyms(String id) throws AnalysisEngineProcessException {
    return getSynonyms(Collections.singletonList(id)).get(id);
  }

  @Override
  public Map<String, Set<String>> getSynonyms(Collection<String> ids)
          throws AnalysisEngineProcessException {
    Map<String, Set<String>> ret = ids.stream().filter(id2synonyms::containsKey)
            .collect(Collectors.toMap(Function.identity(), id2synonyms::get));
    Set<String> mids = Sets.difference(ImmutableSet.copyOf(ids), ret.keySet());
    LOG.info("Retrieved {} from cache, requesting {} missing concepts.", ret.size(), mids.size());
    Map<String, Set<String>> mids2synonysm = delegate.getSynonyms(mids);
    ret.putAll(mids2synonysm);
    id2synonyms.putAll(mids2synonysm);
    db.commit();
    db.getEngine().clearCache();
    return ret;
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
