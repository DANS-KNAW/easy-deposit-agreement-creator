Manual
======

SYNOPSIS
--------

    easy-deposit-agreement-creator [ -s ] <datasetID> <agreement-file>


DESCRIPTION
-----------

A command line tool that creates a pdf document containing the deposit agreement for a given dataset. The tool searches for a dataset that corresponds 
to the given `datasetID` and uses the metadata of this dataset, as well as the personal data of the depositor to generate the deposit agreement.

**Note:** the dataset needs to be in Fedora already. Newly created datasets (for example from `easy-split-multi-deposit`) need to be ingested 
by EASY first before generating the deposit agreement.

`easy-deposit-agreement-creator` uses a template with placeholders. After replacing the placeholders with actual data, the template is converted into a PDF file.

Placeholder substitution is achieved using [Apache Velocity]({{ apache_velocity }}), which fills in and merges a number of template HTML 
files that are specified in `src/main/assembly/dist/res/template/`. Besides data from the dataset, several files in `src/main/assembly/dist/res/` 
are required, namely `dans_logo.png`, `agreement_version.txt`, `Metadataterms.properties` and `velocity-engine.properties`.

Pdf generation based on the assembled HTML is done using the command line tool [WeasyPrint]({{ weasyprint }}). Note that this tool 
requires to be installed before being used by `easy-deposit-agreement-creator`. In order to not having this installed on our computers while developing 
on this project or projects that depend on this project, we use an SSH connection to the development server where the command gets executed. 
During development we therefore require a different script than the one that is used in production. Follow the steps below:

1. In `application.properties` set `pdf.script=localrun.sh`;
2. In `localrun.sh` fill in the variables `USER_HOST` and `PRIVATE_KEY`.

A `--sample` or `-s` flag can be added to the command line tool to signal that a 'sample agreement' needs to be created. This version of the agreement
can be created when the DOI is not yet calculated. Also in the title of the agreement it is clearly indicated that this version is a *sample*.

ARGUMENTS
---------

     -s, --sample    Indicates whether or not a sample agreement needs to be created
     -h, --help      Show help message
     -v, --version   Show version of this program
    
    trailing arguments:
     dataset-id (required)     The ID of the dataset of which a agreement has to be created
     agreement-file (required)   The file location where the agreement needs to be stored


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-deposit-agreement-creator-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /opt/easy-deposit-agreement-creator-<version>/bin/easy-deposit-agreement-creator /usr/bin



General configuration settings can be set in `src/main/assembly/dist/cfg/appliation.properties` and logging can be configured
in `src/main/assembly/dist/cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


**WeasyPrint** is installed according to the [installation page]({{ weasyprint_installation }}) or via:

```
yum install redhat-rpm-config python-devel python-pip python-lxml cairo pango gdk-pixbuf2 libffi-devel weasyprint
```

After this, `weasyprint --help` is supposed to show the appropriate help page.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-deposit-agreement-creator.git
        cd easy-deposit-agreement-creator
        mvn install
