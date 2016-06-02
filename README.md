easy-license-creator
====================
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-license-creator.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-license-creator)

Create a license (pdf file) for a given dataset.

SYNOPSIS
--------

    easy-license-creator <datasetID> <template-dir> <license-file>


DESCRIPTION
-----------

A command line tool that creates a pdf document containing the license for a given dataset. The tool searches for a dataset that corresponds 
to the given `datasetID` and uses the metadata of this dataset, as well as the personal data of the depositor to generate the license.

**Note:** the dataset needs to be in Fedora already. Newly created datasets (for example from `easy-split-multi-deposit`) need to be ingested 
by EASY first before generating the license.

The License Creator uses a template with placeholders. After replacing the placeholders with actual data, the template is converted into a PDF file.

Placeholder substitution is achieved using [Apache Velocity](http://velocity.apache.org/), which fills in and merges a number of template HTML 
files that are specified in `src/main/assembly/dist/res/license/`. Besides data from the dataset, several files in `src/main/assembly/dist/res/` 
are required, namely `dans_logo.jpg`, `license_version.txt`, `Metadataterms.properties` and `velocity-engine.properties`.

Pdf generation based on the assembled HTML is done using the command line tool [WeasyPrint](http://weasyprint.org/). Note that this tool 
requires to be installed before being used by `easy-license-creator`. In order to not having this installed on our computers while developing 
on this project or projects that depend on this project, we use an SSH connection to the development server where the command gets executed. 
During development we therefore require extra settings in `src/main/assembly/dist/cfg/application.properties`:
 
 * `vagrant.user` - the username of *vagrant*
 * `vagrant.host` - the hostname of *vagrant*
 * `vagrant.privatekey` - the path to the private key that is used to ssh into the development server
 
**These fields are *NOT* set when deploying and running on the development, test or production servers!!!** This is why they are commented by default.

ARGUMENTS
---------

    Usage: easy-license-creator <datasetID> <license-file>
    Options:
    
          --help      Show help message
          --version   Show version of this program
    
     trailing arguments:
      dataset-id (required)     The ID of the dataset of which a license has to be created
      license-file (required)   The file location where the license needs to be stored


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-license-creator-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /opt/easy-license-creator-<version>/bin/easy-license-creator /usr/bin



General configuration settings can be set in `src/main/assembly/dist/cfg/appliation.properties` and logging can be configured
in `src/main/assembly/dist/cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


**WeasyPrint** is installed according to the [installation page](http://weasyprint.readthedocs.io/en/latest/install.html) or via:

```
yum install redhat-rpm-configrpm python-devel python-pip python-lxml cairo pango gdk-pixbuf2 libffi-devel weasyprint
```

After this, `weasyprint --help` is supposed to show the appropriate help page.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-license-creator.git
        cd easy-license-creator
        mvn install
