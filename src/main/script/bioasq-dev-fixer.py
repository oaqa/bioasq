import re
import sys
import json
import sqlite3
import editdistance


NEW_YEAR = '2016'
ORIG_YEAR_PATTERN = r'http://www.nlm.nih.gov/cgi/mesh/\d+/(.*)'
UPDT_YEAR_PATTERN = r'http://www.nlm.nih.gov/cgi/mesh/' + NEW_YEAR + r'/\1'

def update_year(question):
    concepts = question.get('concepts', [])
    for i, concept in enumerate(concepts):
        concepts[i] = re.sub(ORIG_YEAR_PATTERN, UPDT_YEAR_PATTERN, concept)


BAD_GO_URL_PATTERN = r'http://amigo.geneontology.org/cgi-bin/amigo/term_details\?term=(\d+)'
FIX_GO_URL_PATTERN = r'http://amigo.geneontology.org/cgi-bin/amigo/term_details?term=GO:\1'

def fix_go_url(question):
    concepts = question.get('concepts', [])
    for i, concept in enumerate(concepts):
        concepts[i] = re.sub(BAD_GO_URL_PATTERN, FIX_GO_URL_PATTERN, concept)


def normalize_yesno_answer(question):
    if question['type'] == 'yesno':
        if question['exact_answer'].lower().startswith('yes'):
            question['exact_answer'] = 'yes'
        else:
            question['exact_answer'] = 'no'


def listify_ideal_answer(question):
    if isinstance(question.get('ideal_answer', None), basestring):
        question['ideal_answer'] = [question['ideal_answer']]


def listify_exact_answer(question):
    if question['type'] == 'list' \
            and isinstance(question.get('exact_answer', None), list) \
            and len(question['exact_answer']) > 0:
        answers = []
        for answer in question['exact_answer']:
            if isinstance(answer, basestring): answers.append([answer])
            elif isinstance(answer, list): answers.append(answer)
        question['exact_answer'] = answers
    if question['type'] == 'factoid' \
            and isinstance(question.get('exact_answer', None), list) \
            and len(question['exact_answer']) > 0 \
            and isinstance(question['exact_answer'][0], list):
        question['exact_answer'] = question['exact_answer'][0]


PARENTHESIS_END_PATTERN = r'([^/\(\)]*)\s+\((.*)\)'

def split_parenthesis_answer(question):
    if question['type'] == 'factoid':
        extra_exact_answers = []
        for exact_answer in question['exact_answer']:
            match = re.match(PARENTHESIS_END_PATTERN, exact_answer)
            if match is not None: extra_exact_answers.extend(match.group(1, 2))
        question['exact_answer'].extend(extra_exact_answers)
    elif question['type'] == 'list':
        for exact_answer in question['exact_answer']:
            extra_ea_element = []
            for ea_element in exact_answer:
                match = re.match(PARENTHESIS_END_PATTERN, ea_element)
                if match is not None: extra_ea_element.extend(match.group(1, 2))
            exact_answer.extend(extra_ea_element)


DOC_URI_PATTERN = r'http://www.ncbi.nlm.nih.gov/pubmed/(\d+)'
QUERY = 'SELECT abstract FROM pmid2abstract WHERE pmid = %s'

def fix_section_label(question, c):
    snippets = question.get('snippets', [])
    for snippet in snippets:
        m = re.match(DOC_URI_PATTERN, snippet['document'])
        docid = m.group(1)
        begin = snippet['offsetInBeginSection']
        end = snippet['offsetInEndSection']
        text = snippet['text']
        if snippet['beginSection'] != 'sections.0': continue
        c.execute(QUERY % docid)
        result = c.fetchone()
        if result is None:
            print docid
            continue
        abstract = result[0]
        if len(abstract) < end: continue
        if editdistance.eval(abstract[begin:end], text) < len(text) * 0.1:
            snippet['beginSection'] = 'abstract'
            snippet['endSection'] = 'abstract'


def remove_fulltext_snippets(question):
    question['snippets'] = [s for s in question.pop('snippets', [])
                              if not s['beginSection'].startswith('sections.')]


def remove_concept_triple_ideal(question):
    question.pop('concepts', None)
    question.pop('triples', None)
    question.pop('ideal_answer', None)


DOC_ID_PATTERN = r'\1'

def replace_doc_uri_with_id(question):
    documents = question.get('documents', [])
    for i, document in enumerate(documents):
        documents[i] = re.sub(DOC_URI_PATTERN, DOC_ID_PATTERN, document)
    snippets = question.get('snippets', [])
    for snippet in snippets:
        snippet['document'] = re.sub(DOC_URI_PATTERN, DOC_ID_PATTERN,
                                     snippet['document'])


def rename_keys(question):
    snippets = question.get('snippets', [])
    for snippet in snippets:
        snippet['section'] = snippet.pop('beginSection')
        snippet.pop('endSection', None)
        snippet['begin'] = snippet.pop('offsetInBeginSection')
        snippet['end'] = snippet.pop('offsetInEndSection')
    if 'exact_answer' in question:
        question['answer'] = question.pop('exact_answer')


def is_yesno_summary(question):
    return question['type'] in set(['yesno', 'summary'])


if __name__ == "__main__":
    filepath = sys.argv[1]
    conn = sqlite3.connect(sys.argv[2])
    c = conn.cursor()
    with open(filepath) as infile:
        data = json.load(infile)
        for (i, question) in enumerate(data['questions']):
            print '%d/%d' % (i, len(data['questions']))
            update_year(question)
            fix_go_url(question)
            normalize_yesno_answer(question)
            listify_ideal_answer(question)
            listify_exact_answer(question)
            split_parenthesis_answer(question)
            fix_section_label(question, c)
            #remove_fulltext_snippets(question)
            #remove_concept_triple_ideal(question) # course
            #replace_doc_uri_with_id(question) # course
            #rename_keys(question) # course
        #data['questions'] = [q for q in data['questions']
        #                       if not is_yesno_summary(q)]  # course
        with open(sys.argv[3], 'w') as outfile:
            json.dump(data, outfile, indent=2)
            outfile.close()
    conn.close()
