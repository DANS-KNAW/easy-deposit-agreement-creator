---
title: Manual
layout: home
---

Manual
======
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-deposit-agreement-creator.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-deposit-agreement-creator)

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

Currently this project is built as an RPM package for RHEL7/CentOS7 and later. The RPM will install the binaries to
`/opt/dans.knaw.nl/easy-deposit-agreement-creator` and the configuration files to `/etc/opt/dans.knaw.nl/easy-deposit-agreement-creator`. 

To install the module on systems that do not support RPM, you can copy and unarchive the tarball to the target host.
You will have to take care of placing the files in the correct locations for your system yourself. For instructions
on building the tarball, see next section.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM

Steps:

    git clone https://github.com/DANS-KNAW/easy-deposit-agreement-creator.git
    cd easy-deposit-agreement-creator
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.

Alternatively, to build the tarball execute:

    mvn clean install assembly:single
