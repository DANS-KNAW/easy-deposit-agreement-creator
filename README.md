easy-test-module
===========
[![Build Status](https://travis-ci.org/DANS-KNAW/easy-license-creator.png?branch=master)](https://travis-ci.org/DANS-KNAW/easy-license-creator)

<Remove this comment and extend the descriptions below>


SYNOPSIS
--------

    easy-license-creator params


DESCRIPTION
-----------

<Replace with a longer description of this module>


ARGUMENTS
---------

<Replace with output from --help option on the command line>


INSTALLATION AND CONFIGURATION
------------------------------


1. Unzip the tarball to a directory of your choice, e.g. /opt/
2. A new directory called easy-license-creator-<version> will be created
3. Add the command script to your `PATH` environment variable by creating a symbolic link to it from a directory that is
   on the path, e.g. 
   
        ln -s /opt/easy-license-creator-<version>/bin/easy-license-creator /usr/bin



General configuration settings can be set in `src/main/assembly/dist/cfg/appliation.properties` and logging can be configured
in `src/main/assembly/dist/cfg/logback.xml`. The available settings are explained in comments in aforementioned files.


BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher

Steps:

        git clone https://github.com/DANS-KNAW/easy-license-creator.git
        cd easy-license-creator
        mvn install
