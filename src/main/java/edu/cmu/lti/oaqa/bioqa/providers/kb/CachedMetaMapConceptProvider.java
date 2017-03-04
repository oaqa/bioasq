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

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * A {@link MetaMapConceptProvider} that uses a local <a href="http://www.mapdb.org/">MapDB</a>
 * instance to cache the service requests and responses.
 *
 * TODO: Created a cache provider and cacheable interface to standardize the cacheable versions of
 * services.
 *
 * @see MetaMapConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/4/15
 */
public class CachedMetaMapConceptProvider extends MetaMapConceptProvider
        implements ConceptProvider {

  private DB db;

  private HTreeMap<String, String> text2mmo;

  private static final Logger LOG = LoggerFactory.getLogger(CachedMetaMapConceptProvider.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // initialize mapdb
    File file = new File((String) getParameterValue("db-file"));
    db = DBMaker.newFileDB(file).compressionEnable().commitFileSyncDisable().cacheSize(1)
            .closeOnJvmShutdown().make();
    String map = (String) getParameterValue("map-name");
    text2mmo = db.getHashMap(map);
    return ret;
  }

  @Override
  protected List<String> requestConcepts(List<String> texts) throws AnalysisEngineProcessException {
    // retrieve cached text/mmos and leave missing ones as null
    List<String> mergedMmoStrings = texts.stream().map(text2mmo::get).collect(toList());
    // find missing indexes and collect texts
    int[] missingIndexes = IntStream.range(0, texts.size())
            .filter(i -> mergedMmoStrings.get(i) == null).toArray();
    LOG.info("{} missing documents.", missingIndexes.length);
    if (missingIndexes.length > 0) {
      List<String> missingTexts = Arrays.stream(missingIndexes).mapToObj(texts::get)
              .collect(toList());
      // retrieve concepts and add to both cache and mergedElements to return
      List<String> missingMmoStrings = super.requestConcepts(missingTexts);
      IntStream.range(0, missingIndexes.length).forEach(i -> {
        String mmoString = missingMmoStrings.get(i);
        mergedMmoStrings.set(missingIndexes[i], mmoString);
        text2mmo.put(missingTexts.get(i), mmoString);
      });
      db.commit();
    }
    db.getEngine().clearCache();
    return mergedMmoStrings;
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
