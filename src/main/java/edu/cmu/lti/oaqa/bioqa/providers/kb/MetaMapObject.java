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

import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.bind.annotation.*;
import java.io.File;
import java.util.List;

/**
 * This class is intended to be used by {@link MetaMapConceptProvider} for serializing MetaMap
 * objects into XML format.
 *
 * @see MetaMapConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 7/12/15.
 */
@XmlRootElement(name = "MMO")
@XmlAccessorType(XmlAccessType.FIELD)
public class MetaMapObject {

  @XmlElementWrapper(name = "Utterances")
  @XmlElement(name = "Utterance")
  private List<Utterance> utterances;

  public List<Utterance> getUtterances() {
    return utterances;
  }

  public void setUtterances(
          List<Utterance> utterances) {
    this.utterances = utterances;
  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Utterance {

    @XmlElementWrapper(name = "Phrases")
    @XmlElement(name = "Phrase")
    private List<Phrase> phrases;

    public List<Phrase> getPhrases() {
      return phrases;
    }

    public void setPhrases(List<Phrase> phrases) {
      this.phrases = phrases;
    }

  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Phrase {

    @XmlElementWrapper(name = "Mappings")
    @XmlElement(name = "Mapping")
    private List<Mapping> mappings;

    public List<Mapping> getMappings() {
      return mappings;
    }

    public void setMappings(List<Mapping> mappings) {
      this.mappings = mappings;
    }

  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Mapping {

    @XmlElementWrapper(name = "MappingCandidates")
    @XmlElement(name = "Candidate")
    private List<Candidate> mappingCandidates;

    public List<Candidate> getMappingCandidates() {
      return mappingCandidates;
    }

    public void setMappingCandidates(List<Candidate> mappingCandidates) {
      this.mappingCandidates = mappingCandidates;
    }

  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class Candidate {

    @XmlElement(name = "CandidateScore")
    private float candidateScore;

    @XmlElement(name = "CandidateCUI")
    private String candidateCUI;

    @XmlElement(name = "CandidateMatched")
    private String candidateMatched;

    @XmlElement(name = "CandidatePreferred")
    private String candidatePreferred;

    @XmlElementWrapper(name = "SemTypes")
    @XmlElement(name = "SemType")
    private List<String> semTypes;

    @XmlElementWrapper(name = "ConceptPIs")
    @XmlElement(name = "ConceptPI")
    private List<ConceptPI> conceptPIs;

    public float getCandidateScore() {
      return candidateScore;
    }

    public void setCandidateScore(float candidateScore) {
      this.candidateScore = candidateScore;
    }

    public String getCandidateCUI() {
      return candidateCUI;
    }

    public void setCandidateCUI(String candidateCUI) {
      this.candidateCUI = candidateCUI;
    }

    public String getCandidateMatched() {
      return candidateMatched;
    }

    public void setCandidateMatched(String candidateMatched) {
      this.candidateMatched = candidateMatched;
    }

    public String getCandidatePreferred() {
      return candidatePreferred;
    }

    public void setCandidatePreferred(String candidatePreferred) {
      this.candidatePreferred = candidatePreferred;
    }

    public List<String> getSemTypes() {
      return semTypes;
    }

    public void setSemTypes(List<String> semTypes) {
      this.semTypes = semTypes;
    }

    public List<ConceptPI> getConceptPIs() {
      return conceptPIs;
    }

    public void setConceptPIs(List<ConceptPI> conceptPIs) {
      this.conceptPIs = conceptPIs;
    }

  }

  @XmlAccessorType(XmlAccessType.FIELD)
  public static class ConceptPI {

    @XmlElement(name = "StartPos")
    private int startPos;

    @XmlElement(name = "Length")
    private int length;

    public int getStartPos() {
      return startPos;
    }

    public void setStartPos(int startPos) {
      this.startPos = startPos;
    }

    public int getLength() {
      return length;
    }

    public void setLength(int length) {
      this.length = length;
    }

  }

  public static void main(String[] args) throws JAXBException {
    JAXBContext jaxbContext = JAXBContext.newInstance(MetaMapObject.class);
    Unmarshaller jaxbUnmarshaller = jaxbContext.createUnmarshaller();
    MetaMapObject mmo = (MetaMapObject) jaxbUnmarshaller.unmarshal(new File(args[0]));
    List<Utterance> us = mmo.getUtterances();
    List<Phrase> ps = us.get(0).getPhrases();
    List<Mapping> ms = ps.get(0).getMappings();
    List<Candidate> cs = ms.get(0).getMappingCandidates();
    List<String> semTypes = cs.get(0).getSemTypes();
    System.out.println("semTypes = " + semTypes);
  }

}