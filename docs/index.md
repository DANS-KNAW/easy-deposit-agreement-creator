---
title: Manual
layout: home
---

Manual
======

TABLE OF CONTENTS
-----------------

- [SYNOPSIS](#synopsis)
- [DESCRIPTION](#description)
- [ARGUMENTS](#arguments)
- [INSTALLATION AND CONFIGURATION](#installation-and-configuration)
- [BUILDING FROM SOURCE](#building-from-source)


SYNOPSIS
--------

    easy-deposit-agreement-creator generate [{--sample|-s}] <datasetId> [<agreement-file>]
    easy-deposit-agreement-creator run-service


DESCRIPTION
-----------

A commandline tool and service that creates a pdf document containing the deposit agreement for a given dataset. The tool searches for a dataset that corresponds 
to the given `datasetId` and uses the metadata of this dataset, as well as the personal data of the depositor to generate the deposit agreement.

**Note:** the dataset needs to be present in Fedora already. Newly created datasets (for example from `easy-split-multi-deposit`) need to be ingested 
by EASY first before generating the deposit agreement using this service. Alternatively, use [`easy-deposit-agreement-generator`] for a service with a JSON-based API.

[`easy-deposit-agreement-generator`]: https://dans-knaw.github.io/easy-deposit-agreement-generator/

A `--sample` or `-s` flag can be added to the commandline tool to signal that a 'sample agreement' needs to be created. This version of the agreement
can be created when the DOI is not yet assigned. Also in the title of the agreement it is clearly indicated that this version is a *sample*.

ARGUMENTS
---------

    Options:
    
      -h, --help      Show help message
      -v, --version   Show version of this program
    
    Subcommand: generate - Generate a deposit agreement for the given datasetId
      -s, --sample   Indicates whether or not a sample agreement needs to be created
      -h, --help     Show help message
    
     trailing arguments:
      datasetId (required)            The datasetId of which a agreement has to be
                                      created
      agreement-file (not required)   The file location where the agreement needs to
                                      be stored. If not provided, the PDF is written
                                      to stdout.
    ---
    
    Subcommand: run-service - Starts EASY Deposit Agreement Creator as a daemon that services HTTP requests
      -h, --help   Show help message
    ---


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-deposit-agreement-creator-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
    ln -s /opt/easy-deposit-agreement-creator-<version>/bin/easy-deposit-agreement-creator /usr/bin

General configuration settings can be set in `src/main/assembly/dist/cfg/appliation.properties` and logging can be configured
in `src/main/assembly/dist/cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

    git clone https://github.com/DANS-KNAW/easy-deposit-agreement-creator.git
    cd easy-deposit-agreement-creator
    mvn install
