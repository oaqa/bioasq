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

import java.util.Collection;

/**
 * This {@link RuntimeException} gives detailed debugging information for
 * {@link TmToolConceptProvider} related errors.
 *
 * @see TmToolConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/25/16.
 */
class TmToolConceptProviderException extends RuntimeException {

  private TmToolConceptProviderException(String message) {
    super(message);
  }

  private TmToolConceptProviderException(String message, Throwable e) {
    super(message, e);
  }

  static TmToolConceptProviderException unequalVolume(String trigger, int sentVolume,
          int recvVolume) {
    return new TmToolConceptProviderException(
            "Unequal volume at " + trigger + ": sent " + sentVolume + " texts and received " +
                    recvVolume + ".");
  }

  static TmToolConceptProviderException unequalTextLength(String trigger, String sentText,
          String recvText) {
    return new TmToolConceptProviderException(
            "Unequal text length at " + trigger + ":\nSent: " + sentText + "\nRecv: " + recvText);
  }

  static TmToolConceptProviderException textChanged(String sentText, String recvText,
          String trigger) {
    return new TmToolConceptProviderException("Error at trigger " + trigger);
  }

  static TmToolConceptProviderException unknownException(String trigger, Throwable e) {
    return new TmToolConceptProviderException("Error at trigger " + trigger, e);
  }

  static TmToolConceptProviderException offsetOutOfBounds(String sentText,
          Collection<PubAnnotation.Denotation> denotations, Throwable e) {
    return new TmToolConceptProviderException(
            "Offset out of bounds:\nSent: " + sentText + "\nSpan: " + denotations);
  }

}
