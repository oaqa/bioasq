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

package edu.cmu.lti.oaqa.baseqa.learning_base;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import org.apache.uima.resource.ResourceSpecifier;

import java.util.Map;

/**
 * An abstract class for the interface {@link CandidateProvider} that can be configured using
 * {@link ConfigurableProvider#initialize(ResourceSpecifier, Map)}}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 5/9/16
 */
public abstract class AbstractCandidateProvider<T> extends ConfigurableProvider
        implements CandidateProvider<T> {
}
