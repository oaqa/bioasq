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

import com.google.gson.Gson;

import java.util.Arrays;
import java.util.List;

/**
 * This class is intended to be used for JSon serialization of PubAnnotation object, which is one
 * of the data formats supported by {@link TmToolConceptProvider}.
 *
 * @see TmToolConceptProvider
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 3/20/16.
 */
public class PubAnnotation {

  private String sourcedb;

  private String sourceid;

  private String text;

  private List<Denotation> denotations;

  public PubAnnotation(String sourcedb, String sourceid, String text) {
    this.sourcedb = sourcedb;
    this.sourceid = sourceid;
    this.text = text;
  }

  public String getSourcedb() {
    return sourcedb;
  }

  public String getSourceid() {
    return sourceid;
  }

  public String getText() {
    return text;
  }

  public List<Denotation> getDenotations() {
    return denotations;
  }

  @Override
  public String toString() {
    return sourcedb + "#" + sourceid + ":" + text + " " + denotations;
  }

  static class Denotation {

    private String obj;

    private Span span;

    public String getObj() {
      return obj;
    }

    public Span getSpan() {
      return span;
    }

    @Override
    public String toString() {
      return obj + "@" + span;
    }
  }

  static class Span {

    private int begin;

    private int end;

    public int getBegin() {
      return begin;
    }

    public int getEnd() {
      return end;
    }

    @Override
    public String toString() {
      return "(" + begin + "," + end + ")";
    }
  }

  public static void main(String[] args) {
    PubAnnotation pa1 = new PubAnnotation("question", "1",
            "Which genes have been found mutated in Gray platelet \n" +
                    "syndrome patients?");
    PubAnnotation pa2 = new PubAnnotation("question", "2",
            "What type of enzyme is peroxiredoxin 2 (PRDX2)?");
    Gson gson = new Gson();
    String json = gson.toJson(new PubAnnotation[] { pa1, pa2 }, PubAnnotation[].class);
    System.out.println("json = " + json);
  }

}
