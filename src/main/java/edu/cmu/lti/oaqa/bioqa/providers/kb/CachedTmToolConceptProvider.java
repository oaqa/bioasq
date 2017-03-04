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
import java.util.*;
import java.util.function.Function;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

/**
 * A {@link TmToolConceptProvider} that uses a local <a href="http://www.mapdb.org/">MapDB</a>
 * instance to cache the service requests and responses.
 *
 * TODO: Created a cache provider and cacheable interface to standardize the cacheable versions of
 * services.
 *
 * @see TmToolConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/27/16
 */
public class CachedTmToolConceptProvider extends TmToolConceptProvider
        implements ConceptProvider {

  private DB db;

  private Map<String, HTreeMap<String, String>> trigger2text2denotations;

  private static final Logger LOG = LoggerFactory.getLogger(CachedTmToolConceptProvider.class);

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // initialize mapdb
    File file = new File((String) getParameterValue("db-file"));
    db = DBMaker.newFileDB(file).compressionEnable().commitFileSyncDisable().cacheSize(1)
            .closeOnJvmShutdown().make();
    String map = (String) getParameterValue("map-name");
    trigger2text2denotations = triggers.stream()
            .collect(toMap(Function.identity(), trigger -> db.getHashMap(map + "/" + trigger)));
    return ret;
  }

  @Override
  protected List<String> requestConcepts(List<String> normalizedTexts, String trigger)
          throws AnalysisEngineProcessException {
    Map<String, String> text2denotations = trigger2text2denotations.get(trigger);
    // retrieve cached text/mmos and leave missing ones as null
    List<String> mergedDenotationStrings = normalizedTexts.stream().map(text2denotations::get)
            .collect(toList());
    // find missing indexes and collect texts
    int[] missingIndexes = IntStream.range(0, normalizedTexts.size())
            .filter(i -> mergedDenotationStrings.get(i) == null).toArray();
    LOG.info("{} missing documents at [{}].", missingIndexes.length, trigger);
    if (missingIndexes.length > 0) {
      List<String> missingTexts = Arrays.stream(missingIndexes).mapToObj(normalizedTexts::get)
              .collect(toList());
      // retrieve concepts and add to both cache and mergedElements to return
      List<String> missingDenotationStrings = super.requestConcepts(missingTexts, trigger);
      IntStream.range(0, missingIndexes.length).forEach(i -> {
        String denotationString = missingDenotationStrings.get(i);
        mergedDenotationStrings.set(missingIndexes[i], denotationString);
        text2denotations.put(missingTexts.get(i), denotationString);
      });
      db.commit();
    }
    db.getEngine().clearCache();
    return mergedDenotationStrings;
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
