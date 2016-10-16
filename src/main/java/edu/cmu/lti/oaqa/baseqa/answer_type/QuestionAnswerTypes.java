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

package edu.cmu.lti.oaqa.baseqa.answer_type;

import com.google.common.collect.HashMultiset;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multiset;
import com.google.gson.*;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.util.stream.Collectors.toMap;

/**
 * A JSon-serializable data object combining question and answer type, which is used in the
 * serialization of the gold-standard labels identified by {@link GSAnswerTypeLabeler}, and
 * deserialization when they are loaded for training by {@link AnswerTypeClassifierTrainer}.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 4/15/16
 */
class QuestionAnswerTypes {

  private String qid;

  // used for gson serialization
  private String question;

  @SerializedName("answer-types")
  private Map<String, Set<String>> answerTypes = new HashMap<>();

  @SerializedName("type-counts")
  private Multiset<String> typeCounts = HashMultiset.create();

  QuestionAnswerTypes(String qid, String question) {
    this.qid = qid;
    this.question = question;
  }

  void addAnswerType(String answer, String type) {
    if (!answerTypes.containsKey(answer)) {
      answerTypes.put(answer, new HashSet<>());
    }
    answerTypes.get(answer).add(type);
    typeCounts.add(type);
  }

  void addAnswerTypes(String answer, Set<String> types) {
    answerTypes.put(answer, types);
    typeCounts.addAll(types);
  }

  QuestionAnswerTypes addQuestionAnswerTypes(QuestionAnswerTypes qat) {
    answerTypes.putAll(qat.answerTypes);
    typeCounts.addAll(qat.typeCounts);
    return this;
  }

  Map<String, Double> getTypeRatios(boolean nullType) {
    double answerCount = answerTypes.size();
    double nullCount = answerTypes.values().stream().filter(Set::isEmpty).count();
    if (nullType) {
      Map<String, Double> typeRatios = typeCounts.entrySet().stream()
              .collect(toMap(Multiset.Entry::getElement, e -> e.getCount() / answerCount));
      typeRatios.put("null", nullCount / answerCount);
      return typeRatios;
    } else {
      Map<String, Double> typeRatios = typeCounts.entrySet().stream().collect(
              toMap(Multiset.Entry::getElement, e -> e.getCount() / (answerCount - nullCount)));
      return typeRatios;
    }
  }

  String getQid() {
    return qid;
  }

  Set<String> getAnswers() {
    return answerTypes.keySet();
  }

  private Map<String, Set<String>> getAnswerTypes() {
    return answerTypes;
  }

  private static class TypeCountsSerializer implements JsonSerializer<Multiset<String>> {

    @Override
    public JsonElement serialize(Multiset<String> src, Type typeOfSrc,
            JsonSerializationContext context) {
      Map<String, Integer> type2count = src.entrySet().stream()
              .collect(toMap(Multiset.Entry::getElement, Multiset.Entry::getCount));
      return context.serialize(type2count);
    }
  }

  private static class TypeCountsDeserializer implements JsonDeserializer<Multiset<String>> {

    @Override
    public Multiset<String> deserialize(JsonElement json, Type typeOfT,
            JsonDeserializationContext context) throws JsonParseException {
      Map<String, Double> type2count = context.deserialize(json, Map.class);
      Multiset<String> ret = HashMultiset.create();
      type2count.entrySet()
              .forEach(entry -> ret.setCount(entry.getKey(), entry.getValue().intValue()));
      return ret;
    }
  }

  static Gson getGson() {
    return new GsonBuilder()
            .registerTypeAdapter(Multiset.class, new TypeCountsSerializer())
            .registerTypeAdapter(Multiset.class, new TypeCountsDeserializer()).setPrettyPrinting()
            .create();
  }

  public static void main(String[] args) {
    Gson gson = getGson();
    QuestionAnswerTypes qat = new QuestionAnswerTypes("test-001", "What is this?");
    qat.addAnswerTypes("aaa", ImmutableSet.of("t1", "t2", "t3"));
    qat.addAnswerTypes("bbb", ImmutableSet.of("t1", "t4", "t5"));
    qat.addAnswerTypes("ccc", ImmutableSet.of());
    String json = gson.toJson(qat, QuestionAnswerTypes.class);
    System.out.println(json);
    QuestionAnswerTypes obj = gson.fromJson(json, QuestionAnswerTypes.class);
    System.out.println(obj.getAnswerTypes());
  }

}
