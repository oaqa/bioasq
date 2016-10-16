Internal Resource Preparation Instruction for OAQA Biomedical Question Answering (BioASQ) System
================================================================================================

_Note:  If you are not a CMU OAQA person, please refer to the general [README](README.md) for preparing the resource._

1. You need to contact [Zi Yang](http://www.cs.cmu.edu/~ziy/) to obtain the UMLS account, if you don't have one nor plan to register one, and our local copies of the resources. Uncompress the `.tgz` file (15G).
    * `bioasq-internal-resources/index` directory has two Lucene indexes
        * `bioasq-internal-resources/index/medline16n-lucene/` is for the Medline corpus
        * `bioasq-internal-resources/index/bioconcept-lucene/` is for the biomedical ontology dumps
    * `bioasq-internal-resources/input` directory contains the test files and the original `4b-dev.json` development set
    * `bioasq-internal-resources/medline16n.db3` is the sqlite database that has the `pmid2abstract` table
1. You need to generate the `4b-dev.json.auto.fulltext` file using `4b-dev.json` and `medline16n.db3`
    1. Install the Python [`editdistance`](https://pypi.python.org/pypi/editdistance) package.
    1. Download the python script [`bioasq-dev-fixer.py`](src/main/script/bioasq-dev-fixer.py)
    1. Fix the formatting errors in the development file.
        ```
        python bioasq-dev-fixer.py path_to_4b-dev.json path_to_medline16n.db3 4b-dev.json.auto.fulltext
        ```
    1. The resulting file should have a md5 of `db72a8fe3f1b3d605b9c39efdd21249d`.
1. Now you can continue on to the `Install` section in the [README](README.md).
