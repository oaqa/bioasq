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

package edu.cmu.lti.oaqa.baseqa.passage;

import com.aliasi.chunk.Chunking;
import com.aliasi.sentences.SentenceChunker;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.uima.jcas.JCas;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.toList;

/**
 * A utility class for retrieval related operations.
 *
 * @author <a href="mailto:ziy@cs.cmu.edu">Zi Yang</a> created on 10/19/14
 */
public class RetrievalUtil {

	public static List<Passage> extractSections(JCas jcas, Document doc) {
		String uri = doc.getUri();
		String docId = doc.getDocId();
		String[] sections = doc.getSections().toArray();
		String[] sectionLabels = doc.getSectionLabels().toArray();
		assert sections.length == sectionLabels.length;
		return IntStream.range(0, sections.length).mapToObj(i -> {
			String t = sections[i];
			String label = sectionLabels[i];
			return TypeFactory.createPassage(jcas, uri, t, docId, 0, t.length(), label, label);
		} ).collect(toList());
	}

	public static List<Passage> extractSentences(JCas jcas, Passage passage,
			SentenceChunker chunker) {
		String text = passage.getText();
		String uri = passage.getUri();
		String docId = passage.getDocId();
		String beginSection = passage.getBeginSection();
		String endSection = passage.getEndSection();
		Chunking chunking = chunker.chunk(text.toCharArray(), 0, text.length());
		return chunking.chunkSet().stream().map(chunk -> {
			int begin = chunk.start();
			int end = chunk.end();
			String t = text.substring(begin, end);
			return TypeFactory.createPassage(jcas, uri, t, docId, begin, end, beginSection, endSection);
		} ).collect(toList());

	}

	public static List<Passage> extractAbstractSection(JCas jcas, Document doc) {
		String uri = doc.getUri();
		String docId = doc.getDocId();
		String[] sections = doc.getSections().toArray();
		String[] sectionLabels = doc.getSectionLabels().toArray();
		assert sections.length == sectionLabels.length;
		return IntStream.range(0, 1).mapToObj(i -> {
			String t = sections[i];
			String label = sectionLabels[i];
			return TypeFactory.createPassage(jcas, uri, t, docId, 0, t.length(), label, label);
		} ).collect(toList());
	}

	public static org.apache.lucene.document.Document createLuceneDocument(Passage passage) {
		org.apache.lucene.document.Document entry = new org.apache.lucene.document.Document();
		entry.add(new StoredField("hash", TypeUtil.hash(passage)));
		entry.add(new TextField("text", passage.getText(), Field.Store.NO));
		return entry;
	}
	
	public static org.apache.lucene.document.Document createLuceneSectionDocument(Passage passage) {
		org.apache.lucene.document.Document entry = new org.apache.lucene.document.Document();
		entry.add(new StoredField("hash", TypeUtil.hash(passage)));
		entry.add(new TextField(passage.getBeginSection(), passage.getText(), Field.Store.NO));
		return entry;
	}

	public static List<Passage> extractTitleAbstract(JCas jcas, Document doc) {
		String uri = doc.getUri();
		String docId = doc.getDocId();
		String title = doc.getTitle();
		String text = doc.getText();
		
		return extractTitleAbstract(jcas, doc,uri,docId,title,text);
	}

	public static List<Passage> extractTitleAbstract(JCas jcas, Document doc,String uri,String docId,String title,String text) 
	{
		
		List<Passage> passages = new ArrayList<>();
		if (title != null) {
			passages.add(TypeFactory.createPassage(jcas, uri, title, docId, 0, title.length(), "title",
					"title"));
		}
		if (text != null) {
			passages.add(TypeFactory.createPassage(jcas, uri, text, docId, 0, text.length(), "abstract",
					"abstract"));
		}
		return passages;
	}

}
