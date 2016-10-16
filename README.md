OAQA Biomedical Question Answering (BioASQ) System 
==================================================

The OAQA Biomedical Question Answering (BioASQ) System aims to identify relevant documents, concepts and passages (snippets) and automatically generate exact answer texts to arbitrary biomedical questions (factoid, list, yes/no). It won the best-performing system in the [BioASQ QA](http://bioasq.org/) Challenges in the factoid and list categories two years in a row in 2015 and 2016 (see [official results](http://participants-area.bioasq.org/oracle/results/taskB/phaseB/)).

System description papers have the most details about the design and implementation of the architecture and the algorithms: 

* Zi Yang, Niloy Gupta, Xiangyu Sun, Di Xu, Chi Zhang, and Eric Nyberg. Learning to Answer Biomedical Factoid & List Questions: OAQA at BioASQ 3B. In Proceedings of _CLEF 2015 Evaluation Labs and Workshop, 2015_. [\[pdf\]](http://www.cs.cmu.edu/~ziy/pubs/BIOASQ15-Yang-et-al.pdf)
* Zi Yang, Yue Zhou, and Eric Nyberg. Learning to Answer Biomedical Questions: OAQA at BioASQ 4B. In Proceedings of _Workshop on Biomedical Natural Language Processing, 2016_. [\[pdf\]](http://www.cs.cmu.edu/~ziy/pubs/BIOASQ16-Yang-et-al.pdf)

Please contact [Zi Yang](http://www.cs.cmu.edu/~ziy/) if you have any questions or comments.


Overview
--------

This system uses the [ECD](https://github.com/oaqa/uima-ecd)/[CSE](https://github.com/oaqa/cse-framework) framework (an extension to the [Apache UIMA framework](https://uima.apache.org/) which support formal, declarative [YAML](http://yaml.org/)-based descriptors for the space of system and component configurations to be explored during system optimization), [BaseQA](https://github.com/oaqa/baseqa) [type system](https://github.com/oaqa/baseqa/blob/master/src/main/resources/baseqa/type/OAQATypes.xml) as well as various natural language processing and information retrieval algorithms and tools.

The system employs a three layered design for both Java source code and YAML descriptors:

| Layer | Description |
| --- | --- |
| `baseqa` | Domain independent QA components, and the basic input/output definition of a QA pipeline, intermediate data objects, QA evaluation components, and data processing components. [\[source\]](src/main/java/edu/cmu/lti/oaqa/baseqa) [\[descriptor\]](src/main/resources/baseqa) |
| `bioqa` | Biomedical resources that can be used in any biomedical QA task (outside the context of BioASQ). [\[source\]](src/main/java/edu/cmu/lti/oaqa/bioqa) [\[descriptor\]](src/main/resources/bioqa) |
| `bioasq` | BioASQ-specific components, e.g. GoPubMed services. [\[source\]](src/main/java/edu/cmu/lti/oaqa/bioasq) [\[descriptor\]](src/main/resources/bioasq) |

Each layer contains packages for each processing step, e.g. preprocess, question analysis, abstract query generation, document retrieval and reranking, concept retrieval and reranking, passage retrieval, answer type prediction, evidence gathering, answer generation and ranking. Please refer to the architecture diagrams in the system description papers

| Workflow | Description | Diagram |
| --- | --- | :---: |
| _Phase A_ | Document, concept, and snippet retrieval | <a href="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-a.png"><img src="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-a.png" width="10%"></a> |
| _Phase B (factoid & list)_ | Exact answer generation for factoid and list questions | <a href="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-b-factlist.png"><img src="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-b-factlist.png" width="10%"></a> | 
| _Phase B (yes/no)_ | Answer prediction for yes/no questions | <a href="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-b-yesno.png"><img src="http://www.cs.cmu.edu/~ziy/images/bioasq-phase-b-yesno.png" width="10%"></a> |

We define the following workflow descriptors (i.e. entry points) under [`bioasq`](src/main/resources/bioasq) for preprocessing, training, evaluation, and testing the Phase A (retrieval tasks) and Phase B (factoid, list and yes/no answer generation).

| Descriptor | Description |
| --- | --- |
| [`preprocess-kb-cache`](src/main/resources/bioasq/preprocess-kb-cache.yaml) | Cache the requests and responses of concept and concept search services |
| [`preprocess-answer-type-gslabel`](src/main/resources/bioasq/preprocess-answer-type-gslabel.yaml) | Label gold-standard answer types |
| [`phase-a-train-concept-document`](src/main/resources/bioasq/phase-a-train-concept-document.yaml) | Train document and concept reranking models |
| [`phase-a-train-snippet`](src/main/resources/bioasq/phase-a-train-snippet.yaml) | Train snippet reranking models |
| [`phase-a-evaluate`](src/main/resources/bioasq/phase-a-evaluate.yaml), [`phase-a-test`](src/main/resources/bioasq/phase-a-test.yaml) | Evaluate (using development subset) and test (using test set) retrieval performance |
| [`phase-b-train-answer-type`](src/main/resources/bioasq/phase-b-train-answer-type.yaml) | Train answer type prediction model for factoid and list questions |
| [`phase-b-train-answer-score`](src/main/resources/bioasq/phase-b-train-answer-score.yaml) | Train answer scoring model for factoid and list questions |
| [`phase-b-train-answer-collective-score`](src/main/resources/bioasq/phase-b-train-answer-collective-score.yaml) | Train answer collective scoring model for list questions |
| [`phase-b-train-yesno`](src/main/resources/bioasq/phase-b-train-yesno.yaml) | Train yes/no prediction model |
| [`phase-b-evaluate-factoid-list`](src/main/resources/bioasq/phase-b-evaluate-factoid-list.yaml), [`phase-b-test-factoid-list`](src/main/resources/bioasq/phase-b-test-factoid-list.yaml) | Evaluate (using development subset) and test (using test set) factoid and list QA |
| [`phase-b-evaluate-yesno`](src/main/resources/bioasq/phase-b-evaluate-yesno.yaml), [`phase-b-test-yesno`](src/main/resources/bioasq/phase-b-test-yesno.yaml) | Evaluate (using development subset) and test (using test set) yes/no QA |

A workflow descriptor can be executed by the [`ECDDriver`](https://github.com/oaqa/uima-ecd/blob/master/src/main/java/edu/cmu/lti/oaqa/ecd/driver/ECDDriver.java), which has been configured as the main class in the Maven `exec` goal, and thus it can be executed from the command line with the `config` specified as the `path.to.the.descriptor`.

The system also depends on other types of resources, including [`dictionaries`](src/main/resources/dictionaries), pretrained machine learning [`models`](src/main/resources/models), and service related [`properties`](src/main/resources/properties).


### Change Notes

- Update Lucene from version 5.5.1 to 6.2.1, which results in change of default similarity.
- Bug fixes


Setting Up the System
---------------------

### Prerequisites

This system needs to access external structured and unstructured resources for question answering and files for evaluating the system. Due to licensing issues, you may have to obtain these resources or credentials on your own. If you are a CMU OAQA person, please read the [internal resource preparation instruction](INTERNAL_INSTRUCTION.md) instead.

* __Pre-prerequisites__. Java 8, Maven 3, Python 2.

* (_Recommended_) __UMLS license/account.__ The system needs to access the online UMLS services (UTS and MetaMap), which require UMLS license/account (username, password, email). You can request them from https://uts.nlm.nih.gov//license.html. Otherwise, you need to remove all the `*-uts-*` and `*-metamap-*` steps from the descriptors, which will hugely hurt the performance.

    If you want to increase the system's throughput, you may consider to download and install local instances of UMLS and MetaMap services. Currently, we only have the Web services integrated.

* (_Recommended_) __Medline corpus and Lucene index.__ The system can use a local Medline index or the GoPubMed Web API for searching the PubMed. However, we recommend a local index because the reranking component may send up to hundreds of search requests per question. Using a Web API can take forever to process one question.

    1. Download `.xml.gz` or `.xml` files from https://www.nlm.nih.gov/databases/download/pubmed_medline.html.

    1. (_Optional_) Check out the [`medline-indexer`](https://github.com/ziy/medline-indexer) project.

    1. Create a Lucene index using the `StandardAnalyzer`. The index should contain three mandatory fields: `pmid`, `abstractText`, and `articleTitle`. We include an example Java code [`MedlineCitationIndexer.java`](https://github.com/ziy/medline-indexer/blob/master/src/main/java/edu/cmu/lti/oaqa/bio/index/medline/MedlineCitationIndexer.java) that indexes `.xml.gz` or `.xml` files inside a directory.

    1. Create a sqlite database that has a `pmid2abstract` table with two fields `pmid` and `abstract`, which is used to fix the section label errors in the provided development set. We include an example Java code [`MedlineAbstractStoreBuilder.java`](https://github.com/ziy/medline-indexer/blob/master/src/main/java/edu/cmu/lti/oaqa/bio/index/medline/MedlineAbstractStoreBuilder.java) that builds the sqlite file. 

* __Biomedical ontology dumps and Lucene index.__ You can skip this step if you don't need relevant concept retrieval, but please also remove the `concept-retrieval` and `concept-rerank` steps from the descriptors if you do so. If you prefer using a local biomedical ontology index (recommended) to the official GoPubMed services, you need to obtain the ontology dumps and create a Lucene index.

    1. Download the ontology dumps from all the sources according to the [official resources guideline document](http://participants-area.bioasq.org/Tasks/b/resources/).
    
    1. (_Optional_) Check out the [`biomedical-concept-indexer`](https://github.com/YueChou/biomedical-concept-indexer) project.
    
    1. Create a Lucene index. The index should contain four mandatory fields: `id`, `name`, `definition`, and `source`. Different sources of ontologies need to be adapted into the same single schema, and specify the `source` and `id` of the concept in the original ontology source. `Definition` and `name` fields are intended to be used for retrieval. We include an example Java code [`BiomedicalConceptIndexer.java`](https://github.com/YueChou/biomedical-concept-indexer/blob/master/src/main/java/edu/cmu/lti/oaqa/bio/index/concept/BiomedicalConceptIndexer.java) that indexes multiple ontologies.
    
* __BioASQ development and test files.__ You will need the test files for `*-test-*` workflows and the development files for `*-evaluate-*` and `*-train-*` workflows. However, the official development file has various errors. We created a python script [`bioasq-dev-fixer.py`](src/main/script/bioasq-dev-fixer.py) to fix the errors, include `update_year`, `fix_go_url`, `normalize_yesno_answer`, `listify_ideal_answer`, `listify_exact_answer`, `split_parenthesis_answer`, `fix_section_label`, etc.

    1. Obtain the test set and development set (containing the gold-standard answers) from the [BioASQ website](http://participants-area.bioasq.org/).
    
    1. Install the Python [`editdistance`](https://pypi.python.org/pypi/editdistance) package.
    
    1. Use the provided script to fix the formatting errors in the development file.
        ```
        python bioasq-dev-fixer.py path_to_orig_4b_dev_set path_to_pmid2abstract_db 4b-dev.json.auto.fulltext
        ```
        
    1. The resulting file should have a md5 of `8751b3a962eafb5c2aa8f09d5998fcd4`.
    
* (_Optional_) __PubMed Central corpus and document service.__ Since the PubMed Central full text is no longer used in the evaluation from BioASQ 2016, it is _not_ integrated into the predefined workflow descriptors. If you plan to use the PubMed Central corpus for passage retrieval (see below), you also need to download the PMC corpus and set up a document server.

    1. Download the PMC open access subset: https://www.ncbi.nlm.nih.gov/pmc/tools/ftp/
    
    1. Use the BioASQTasks.jar (provided in the [official preparation package](http://participants-area.bioasq.org/Tasks/b/resources/) prior to 2015) to convert the xml files to a single JSON Array file.
        ```
        java -jar BioASQTasks.jar
        ```
        
    1. Create a directory `pmc` and split the JSON Array file to individual JSON documents, each containing a single document and named by its PMID, and put into the `pmc` directory.
    
    1. Set up a HTTP document server with the resource root being the directory that contains `pmc` directory. Make sure you can access each document using the URL: `http://HOST:PORT/pmc/DOC_ID`.


### Install

1. Clone the project into a local directory.

1. Put the test json files into the `input` directory, and rename them to `dryrun-a.json`, `dryrun-b.json`, `1b-1-a.json`, ..., `4b-5-b.json`. (Read the `collection-reader.file` parameter value in each descriptor to understand what the system will look for.) If you use a customized input directory and/or json file names, please change the `collection-reader.file` parameter in the workflow descriptor.

1. Create the `result` directory under the project folder, which is used for the system final output. If you use a customized output directory, you can change the following descriptors
    * [`resources/baseqa/cas-serialize.yaml`](src/main/resources/baseqa/cas-serialize.yaml)
    * [`resources/bioasq/collection/json/json-cas-consumer.yaml`](src/main/resources/bioasq/collection/json/json-cas-consumer.yaml)

1. Create the `persistence` directory and download the [`oaqa-cse.db3`](https://github.com/oaqa/emptydb/raw/master/oaqa-cse.db3) file into the `persistence` folder. As this project uses the [CSE framework](https://github.com/oaqa/cse-framework), the sqlite database persists the experiment metadata, the intermediate data objects (optionally) and the evaluation results for debugging and reporting purposes. If you use a customized persistence directory and/or file name, you can create your own `persistence-provider` descriptor and update the `persistence-provider` parameters wherever used.

1. Create `concept-search-cache`, `metamap-cache`, `synonym-cache`, and `tmtool-cache` directories under `src/main/resources/` directory. If you don't need the cache, you can replace the `*-cached` descriptors with the non-cached versions (direct access). If you use a customized cache directories, you need to update the `db-file` parameter in the `*-cached` descriptors, including
    * [`resources/bioqa/providers/kb/concept-search-uts-cached.yaml.template`](src/main/resources/bioqa/providers/kb/concept-search-uts-cached.yaml.template)
    * [`resources/bioqa/providers/kb/metamap-cached.yaml.template`](src/main/resources/bioqa/providers/kb/metamap-cached.yaml.template)
    * [`resources/bioqa/providers/kb/synonym-uts-cached.yaml.template`](src/main/resources/bioqa/providers/kb/synonym-uts-cached.yaml.template)
    * [`resources/bioqa/providers/kb/tmtool-cached.yaml.template`](src/main/resources/bioqa/providers/kb/tmtool-cached.yaml.template)
    
    (_Checkpoint_) At this point, the project structure should look like this unless you have customized it.

     ```
     |-- bioasq/
     |   |-- input/
     |   |   |-- 1b-1-a.json
     |   |   |-- .
     |   |   |-- .
     |   |   |-- .
     |   |   |-- 4b-5-b.json
     |   |   |-- 4b-dev.json.auto.fulltext
     |   |   |-- dryrun-a.json
     |   |   |-- dryrun-b.json
     |   |   |-- one-question.json
     |   |-- persistence/
     |   |   |-- oaqa-cse.db3
     |   |-- result/
     |   |-- src/
     |   |   |-- main/
     |   |   |   |-- java/
     |   |   |   |-- resources/
     |   |   |   |   |-- baseqa/
     |   |   |   |   |-- bioasq/
     |   |   |   |   |-- bioqa/
     |   |   |   |   |-- concept-search-cache/
     |   |   |   |   |-- dictionaries/
     |   |   |   |   |-- metamap-cache/
     |   |   |   |   |-- models/
     |   |   |   |   |-- synonym-cache/
     |   |   |   |   |-- tmtool-cache/
     |   |   |   |-- script/
     ```
1. Update the `index` parameter in the `lucene-bioconcept` descriptors with the path to the Lucene index for the biomedical ontology. Also, you need to change other parameters if you use customized field names. Remove the `.template` suffix from the file names, including 
    * [`resources/bioqa/concept/retrieval/lucene-bioconcept.yaml.template`](src/main/resources/bioqa/concept/retrieval/lucene-bioconcept.yaml.template)
    * [`resources/bioqa/concept/rerank/scorers/lucene-bioconcept.yaml.template`](src/main/resources/bioqa/concept/rerank/scorers/lucene-bioconcept.yaml.template)
    
1. Update the `index` parameter in the `lucene-medline` descriptors with the path to the Lucene Medline index. Also, you need to change other parameters if you use customized field names. Remove the `.template` suffix from the file names, including 
    * [`resources/bioqa/document/retrieval/lucene-medline.yaml.template`](src/main/resources/bioqa/document/retrieval/lucene-medline.yaml.template)
    * [`resources/bioqa/document/rerank/scorers/lucene-medline.yaml.template`](src/main/resources/bioqa/document/rerank/scorers/lucene-medline.yaml.template) 
    
1. Update the `version` (e.g. `1415`), `username`, `password`, and `email` parameters in the `uts` and `metamap` related providers, and remove the `.template` suffix from the file names, including
    * [`resources/bioqa/proviers/kb/concept-search-uts.yaml.template`](src/main/resources/bioqa/proviers/kb/concept-search-uts.yaml.template)
    * [`resources/bioqa/proviers/kb/concept-search-uts-cached.yaml.template`](src/main/resources/bioqa/proviers/kb/concept-search-uts-cached.yaml.template)
    * [`resources/bioqa/proviers/kb/metamap.yaml.template`](src/main/resources/bioqa/proviers/kb/metamap.yaml.template)
    * [`resources/bioqa/proviers/kb/metamap-cached.yaml.template`](src/main/resources/bioqa/proviers/kb/metamap-cached.yaml.template)
    * [`resources/bioqa/proviers/kb/synonym-uts.yaml.template`](src/main/resources/bioqa/proviers/kb/synonym-uts.yaml.template)
    * [`resources/bioqa/proviers/kb/synonym-uts-cached.yaml.template`](src/main/resources/bioqa/proviers/kb/synonym-uts-cached.yaml.template)
    
1. Install the dependencies and compile the resources via Maven:
    ```
    mvn clean compile
    ```
    
    When you see `BUILD SUCCESS`, the installation is done.
   

### Test on BioASQ Test Set

1. (_Optional_, _Recommended_) Execute the `preprocess-kb-cache` workflow if you haven't done yet:
    ```
    mvn exec:exec -Dconfig=bioasq.preprocess-kb-cache
    ```
    
    At the end of the execution, you should see mapdb files generated in the `*-cache` directories. This step could be extremely slow (> 10 hours) depending on the workload on the UTS/MetaMap servers.
    
1. Execute any `*-test-*` workflow descriptor to test the pipeline:
    ```
    mvn exec:exec -Dconfig=bioasq.phase-a-test
    mvn exec:exec -Dconfig=bioasq.phase-b-test-factoid-list
    mvn exec:exec -Dconfig=bioasq.phase-b-test-yest-no
    ```
    
1. You should see the output in the `result` directory at the end of each execution.


### Evaluate on BioASQ Test Set

The common evaluation metrics are defined in the BaseQA project's [`eval`](https://github.com/oaqa/baseqa/tree/master/src/main/java/edu/cmu/lti/oaqa/baseqa/eval) package. The system extends the evaluation metrics for the BioASQ task in the [`eval`](src/main/java/edu/cmu/lti/oaqa/bioasq/eval) package. All the `*-evaluate-*` descriptors add additional `post-process`ing steps to generate the evaluation results automatically.

1. Put the `4b-dev.json.auto.fulltext` file under the directory `input` if you haven't done yet. If you use a customized directory and/or file name, you need to change the [`resources/bioasq/gs/bioasq-qa-decorator.yaml`](src/main/resources/bioasq/gs/bioasq-qa-decorator.yaml) descriptor content accordingly.

1. (_Optional_, _Recommended_) Execute the `preprocess-kb-cache` workflow if you haven't done yet, and at the end of the execution, you should see mapdb files generated in the `*-cache` directories.

1. Execute any `*-evaluate-*` workflow descriptor to test the pipeline.
    ```
    mvn exec:exec -Dconfig=bioasq.phase-a-evaluate
    mvn exec:exec -Dconfig=bioasq.phase-b-evaluate-factoid-list
    mvn exec:exec -Dconfig=bioasq.phase-b-evaluate-yest-no
    ```
    
1. You should see the evaluation results at the end of the execution in the console. 

    For example,
    ```
    Experiment: 8f5876cc-7dcf-41c2-9da3-7fe841ae92d9:1
    traceId,Answer/Answer/YESNO_COUNT,Answer/Answer/YESNO_MEAN_ACCURACY,Answer/Answer/YESNO_MEAN_NEG_ACCURACY,Answer/Answer/YESNO_MEAN_POS_ACCURACY
    1|QuestionParser[inherit:bioqa.question.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics]>2|QuestionConceptRecognizer[inherit:bioqa.question.concept.metamap-cached#concept-provider:inherit: bioqa.providers.kb.metamap-cached]>3|QuestionConceptRecognizer[inherit:bioqa.question.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached]>4|QuestionConceptRecognizer[inherit:bioqa.question.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia]>5|PassageToViewCopier[inherit:baseqa.evidence.passage-to-view#view-name-prefix:ptv]>6|PassageParser[inherit:bioqa.evidence.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics#view-name-prefix:ptv]>7|PassageConceptRecognizer[inherit:bioqa.evidence.concept.metamap-cached#allowed-concept-types:/dictionaries/allowed-umls-types.txt#concept-provider:inherit: bioqa.providers.kb.metamap-cached#view-name-prefix:ptv]>8|PassageConceptRecognizer[inherit:bioqa.evidence.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached#view-name-prefix:ptv]>9|PassageConceptRecognizer[inherit:bioqa.evidence.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia#view-name-prefix:ptv]>10|PassageConceptRecognizer[inherit:baseqa.evidence.concept.frequent-phrase#concept-provider:inherit: baseqa.providers.kb.frequent-phrase#view-name-prefix:ptv]>11|ConceptSearcher[inherit:bioqa.evidence.concept.search-uts-cached#concept-search-provider:inherit: bioqa.providers.kb.concept-search-uts-cached#synonym-expansion-provider:inherit: bioqa.providers.kb.synonym-uts-cached]>12|ConceptMerger[inherit:baseqa.evidence.concept.merge#include-default-view:true#view-name-prefix:ptv#use-name:true]>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.liblinear-predict#classifier:inherit: bioqa.answer.yesno.liblinear#feature-file:result/answer-yesno-predict-liblinear.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ],28.0000,0.5714,0.3333,0.6842
    1|QuestionParser[inherit:bioqa.question.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics]>2|QuestionConceptRecognizer[inherit:bioqa.question.concept.metamap-cached#concept-provider:inherit: bioqa.providers.kb.metamap-cached]>3|QuestionConceptRecognizer[inherit:bioqa.question.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached]>4|QuestionConceptRecognizer[inherit:bioqa.question.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia]>5|PassageToViewCopier[inherit:baseqa.evidence.passage-to-view#view-name-prefix:ptv]>6|PassageParser[inherit:bioqa.evidence.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics#view-name-prefix:ptv]>7|PassageConceptRecognizer[inherit:bioqa.evidence.concept.metamap-cached#allowed-concept-types:/dictionaries/allowed-umls-types.txt#concept-provider:inherit: bioqa.providers.kb.metamap-cached#view-name-prefix:ptv]>8|PassageConceptRecognizer[inherit:bioqa.evidence.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached#view-name-prefix:ptv]>9|PassageConceptRecognizer[inherit:bioqa.evidence.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia#view-name-prefix:ptv]>10|PassageConceptRecognizer[inherit:baseqa.evidence.concept.frequent-phrase#concept-provider:inherit: baseqa.providers.kb.frequent-phrase#view-name-prefix:ptv]>11|ConceptSearcher[inherit:bioqa.evidence.concept.search-uts-cached#concept-search-provider:inherit: bioqa.providers.kb.concept-search-uts-cached#synonym-expansion-provider:inherit: bioqa.providers.kb.synonym-uts-cached]>12|ConceptMerger[inherit:baseqa.evidence.concept.merge#include-default-view:true#view-name-prefix:ptv#use-name:true]>13|AllYesYesNoAnswerPredictor[inherit:baseqa.answer.yesno.all-yes],28.0000,0.6786,0.0000,1.0000
    1|QuestionParser[inherit:bioqa.question.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics]>2|QuestionConceptRecognizer[inherit:bioqa.question.concept.metamap-cached#concept-provider:inherit: bioqa.providers.kb.metamap-cached]>3|QuestionConceptRecognizer[inherit:bioqa.question.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached]>4|QuestionConceptRecognizer[inherit:bioqa.question.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia]>5|PassageToViewCopier[inherit:baseqa.evidence.passage-to-view#view-name-prefix:ptv]>6|PassageParser[inherit:bioqa.evidence.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics#view-name-prefix:ptv]>7|PassageConceptRecognizer[inherit:bioqa.evidence.concept.metamap-cached#allowed-concept-types:/dictionaries/allowed-umls-types.txt#concept-provider:inherit: bioqa.providers.kb.metamap-cached#view-name-prefix:ptv]>8|PassageConceptRecognizer[inherit:bioqa.evidence.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached#view-name-prefix:ptv]>9|PassageConceptRecognizer[inherit:bioqa.evidence.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia#view-name-prefix:ptv]>10|PassageConceptRecognizer[inherit:baseqa.evidence.concept.frequent-phrase#concept-provider:inherit: baseqa.providers.kb.frequent-phrase#view-name-prefix:ptv]>11|ConceptSearcher[inherit:bioqa.evidence.concept.search-uts-cached#concept-search-provider:inherit: bioqa.providers.kb.concept-search-uts-cached#synonym-expansion-provider:inherit: bioqa.providers.kb.synonym-uts-cached]>12|ConceptMerger[inherit:baseqa.evidence.concept.merge#include-default-view:true#view-name-prefix:ptv#use-name:true]>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.weka-logistic-predict#classifier:inherit: bioqa.answer.yesno.weka-logistic#feature-file:result/answer-yesno-predict-weka-logistic.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ],28.0000,0.6429,0.2222,0.8421
    1|QuestionParser[inherit:bioqa.question.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics]>2|QuestionConceptRecognizer[inherit:bioqa.question.concept.metamap-cached#concept-provider:inherit: bioqa.providers.kb.metamap-cached]>3|QuestionConceptRecognizer[inherit:bioqa.question.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached]>4|QuestionConceptRecognizer[inherit:bioqa.question.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia]>5|PassageToViewCopier[inherit:baseqa.evidence.passage-to-view#view-name-prefix:ptv]>6|PassageParser[inherit:bioqa.evidence.parse.clearnlp-bioinformatics#parser-provider:inherit: bioqa.providers.parser.clearnlp-bioinformatics#view-name-prefix:ptv]>7|PassageConceptRecognizer[inherit:bioqa.evidence.concept.metamap-cached#allowed-concept-types:/dictionaries/allowed-umls-types.txt#concept-provider:inherit: bioqa.providers.kb.metamap-cached#view-name-prefix:ptv]>8|PassageConceptRecognizer[inherit:bioqa.evidence.concept.tmtool-cached#concept-provider:inherit: bioqa.providers.kb.tmtool-cached#view-name-prefix:ptv]>9|PassageConceptRecognizer[inherit:bioqa.evidence.concept.lingpipe-genia#concept-provider:inherit: bioqa.providers.kb.lingpipe-genia#view-name-prefix:ptv]>10|PassageConceptRecognizer[inherit:baseqa.evidence.concept.frequent-phrase#concept-provider:inherit: baseqa.providers.kb.frequent-phrase#view-name-prefix:ptv]>11|ConceptSearcher[inherit:bioqa.evidence.concept.search-uts-cached#concept-search-provider:inherit: bioqa.providers.kb.concept-search-uts-cached#synonym-expansion-provider:inherit: bioqa.providers.kb.synonym-uts-cached]>12|ConceptMerger[inherit:baseqa.evidence.concept.merge#include-default-view:true#view-name-prefix:ptv#use-name:true]>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.weka-cvr-predict#classifier:inherit: bioqa.answer.yesno.weka-cvr#feature-file:result/answer-yesno-predict-weka-cvr.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ],28.0000,0.6071,0.6667,0.5789
    ```
    
    For better visualization, you can split the lines into cells using the comma separators, like this:
    
    | traceId | COUNT | ACCURACY | NEG_ACCURACY | POS_ACCURACY |
    | --- | ---: | ---: | ---: | ---: |
    | `...>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.liblinear-predict#classifier:inherit: bioqa.answer.yesno.liblinear#feature-file:result/answer-yesno-predict-liblinear.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ]` | 28.0000 | 0.5714 | 0.3333 | 0.6842 |
    | `...>13|AllYesYesNoAnswerPredictor[inherit:baseqa.answer.yesno.all-yes]` | 28.0000 | 0.6786 | 0.0000 | 1.0000 |
    | `...>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.weka-logistic-predict#classifier:inherit: bioqa.answer.yesno.weka-logistic#feature-file:result/answer-yesno-predict-weka-logistic.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ]` | 28.0000 | 0.6429 | 0.2222 | 0.8421 |
    | `...>13|YesNoAnswerPredictor[inherit:bioqa.answer.yesno.weka-cvr-predict#classifier:inherit: bioqa.answer.yesno.weka-cvr#feature-file:result/answer-yesno-predict-weka-cvr.tsv#scorers:- inherit: baseqa.answer.yesno.scorers.concept-overlap - inherit: bioqa.answer.yesno.scorers.token-overlap - inherit: baseqa.answer.yesno.scorers.expected-answer-overlap - inherit: baseqa.answer.yesno.scorers.sentiment - inherit: baseqa.answer.yesno.scorers.negation - inherit: bioqa.answer.yesno.scorers.alternate-answer ]` | 28.0000 | 0.6071 | 0.6667 | 0.5789 |
    
    
### Retrain the Models

The system includes pretrained models using the predefined `*-train-*` descriptors (i.e. 4b-dev set _minus_ 4b-5 test set). However, if you plan to retrain the models, you can follow these steps. Please be aware that the models are saved under `resources/models`, and loaded from classpath directly, which means you might want to recompile the project using `mvn clean compile` to copy the newly generated models into the `target` directory between the training processes, so that the next training can use the models from the previous one.

1. Put the `4b-dev.json.auto.fulltext` file under the directory `input` if you haven't done so. If you use a customized directory and/or gold-standard file, you need to change the [`resources/bioasq/gs/bioasq-qa-decorator.yaml`](src/main/resources/bioasq/gs/bioasq-qa-decorator.yaml) descriptor content accordingly.

1. (_Optional_, _Recommended_) Execute the `preprocess-kb-cache` workflow if you haven't done yet, and at the end of the execution, you should see mapdb files generated in the `*-cache` directories.

1. Execute the `preprocess-answer-type-gslabel` workflow if you haven't done yet, and at the end of the execution, you should see `4b-dev-gslabel-tmtool.json` and `4b-dev-gslabel-uts.json` files generated in the [`resources/models/bioqa/answer_type`](src/main/resources/models/bioqa/answer_type) directories.
    ```
    mvn clean compile exec:exec -Dconfig=bioasq.preprocess-answer-type-gslabel
    ```
    
    This step could take about 30 minutes.
    
1. Training Phase A requires execution of `phase-a-train-concept-document` before `phase-a-train-snippet`.
    ```
    mvn clean compile exec:exec -Dconfig=bioasq.phase-a-train-concept-document
    mvn clean compile exec:exec -Dconfig=bioasq.phase-a-train-snippet
    ```
    
    Executing `phase-a-train-concept-document` could take 3-4 hours, and executing `phase-a-train-snippet` could take 80 minutes.
    
    Training Phase B factoid and list QA requires execution of `phase-b-train-answer-type` first, then `phase-b-train-answer-score`, and finally `phase-b-train-answer-collective-score`.
    ```
    mvn clean compile exec:exec -Dconfig=bioasq.phase-b-train-answer-type
    mvn clean compile exec:exec -Dconfig=bioasq.phase-b-train-answer-score
    mvn clean compile exec:exec -Dconfig=bioasq.phase-b-train-answer-collective-score
    ```
    
    Executing `phase-b-train-answer-type` or `phase-b-train-answer-score` could take 30 minutes each. Executing `phase-b-train-answer-collective-score` could take 10 minutes.
    
    Training Phase B yes/no QA requires execution of `phase-b-train-yesno`.
    ```
    mvn clean compile exec:exec -Dconfig=bioasq.phase-b-train-yesno
    ```
    
    Executing `phase-b-train-answer-collective-score` could take about 10 minutes.
     
1. You should see cross-validation results at the end of each training. 


### Test on Arbitrary Biomedical Questions

You can use your own biomedical questions to test the system in either Phase A or Phase B, similar to testing on BioASQ test set.


#### For Phase A

1. You can refer to the [`input/one-question.json`](src/main/resources/input/one-question.json) file, and update the question.

    ```json
    {
      "questions": [
        {
          "body": "What is the role of MMP-1 in breast cancer?",
          "type": "factoid",
          "id": "0"
        }
      ]
    }
    ```
    
1. You need to change the `collection-reader.file` parameter to `input/one-question.json` in the `phase-a-test` descriptor to test Phase A.


#### For Phase B

1. You need to _manually_ add relevant snippets to the [`input/one-question.json`](src/main/resources/input/one-question.json) file, similar to the Phase B test file (i.e. `*b-*-b.json`).

1. You need to change the `collection-reader.file` parameter to `input/one-question.json` in the `phase-b-test-factoid-list` descriptor to test Phase B.

We are working on testing an end-to-end QA system that combines Phase A and Phase B workflows. You may also creatively combine the steps from both descriptors on your own. 


### (_Advanced_, _Optional_) Use the PubMed Central Content

Since the PubMed Central full text is not used in the evaluation from BioASQ 2016, it is _not_ integrated into the predefined workflow descriptors. However, you can still use it for relevant passage retrieval.

1. Make sure you have the PubMed Central full text and document server.

1. Update the `url-format` parameter in the [`resources/bioasq/passage/pmc-content.yaml.template`](src/main/resources/bioasq/passage/pmc-content.yaml.template) with the PubMed Central document server URL, and remove the `.template` suffix from the file name.

1. Add the `pmc-content` step after the `document-retrieval`/`document-rerank` step, but before `passage-retrieval` step, in the descriptor.


### (_Advanced_, _Optional_) Use a Local or Proxy GoPubMed Server

The official GoPubMed is sometimes slow. If you use a local or proxy GoPubMed server different from the official server, as those specified in the [`properties`](src/main/resources/properties/) folder, and you plan to use the GoPubMed components, which are _not_ used the predefined workflow descriptors, you can change the `conf` parameter in the `gopubmed` related descriptors, including
  * [`resources/bioasq/concept/retrieval/gopubmed.yaml`](src/main/resources/bioasq/concept/retrieval/gopubmed.yaml)
  * [`resources/bioasq/concept/retrieval/gopubmed-separate.yaml`](src/main/resources/bioasq/concept/retrieval/gopubmed-separate.yaml)
  * [`resources/bioasq/concept/rerank/scorers/gopubmed.yaml`](src/main/resources/bioasq/concept/rerank/scorers/gopubmed.yaml)
  * [`resources/bioasq/document/retrieval/gopubmed.yaml`](src/main/resources/bioasq/document/retrieval/gopubmed.yaml)
  * [`resources/bioasq/triple/retrieval/gopubmed.yaml `](src/main/resources/bioasq/triple/retrieval/gopubmed.yaml) 


Component Development
---------------------

The system is far from perfect, and it needs tuning and component development. In addition to the system description papers, you may also read the [UIMA and OAQA Tutorial](https://github.com/oaqa/oaqa-tutorial/wiki/Tutorial) to get familiar with the UIMA/ECD/CSE frameworks used by this system.


Acknowledgement
---------------

We thank Ying Li, Xing Yang, Venus So, James Cai and the other team members at Roche Innovation Center New York for their support of OAQA and biomedical question answering research and development.


License
-------

This project is licensed under the Apache License ver 2.0 - see the [LICENSE.txt](LICENSE.txt) file for details. However, please note that some third-party dependencies may be licensed differently.
