---
title: Manual
layout: home
---

Manual
======

TABLE OF CONTENTS
-----------------



SYNOPSIS
--------

    easy-deposit-agreement-creator run-service
                          

DESCRIPTION
-----------
This service offers a JSON-based API that lets the client generate a Deposit Agreement based on provided parameters.

For details about the service API see the [OpenAPI specification].

[OpenAPI specification]: ./api.html

ARGUMENTS
---------

          --help                Show help message
          --version             Show version of this program

    Subcommand: run-service - Starts the Deposit Agreement Creator as a daemon that services
                              HTTP requests
          --help   Show help message
    ---


INSTALLATION AND CONFIGURATION
------------------------------
The preferred way of install this module is using the RPM package. This will install the binaries to
`/opt/dans.knaw.nl/easy-deposit-agreement-creator`, the configuration files to `/etc/opt/dans.knaw.nl/easy-deposit-agreement-creator`,
and will install the service script for `initd` or `systemd`.

If you are on a system that does not support RPM, you can use the tarball. You will need to copy the
service scripts to the appropiate locations yourself.

BUILDING FROM SOURCE
--------------------

Prerequisites:

* Java 8 or higher
* Maven 3.3.3 or higher
* RPM (if you want to build the RPM package).

Steps:

    git clone https://github.com/DANS-KNAW/easy-deposit-agreement-creator.git
    cd easy-deposit-agreement-creator
    mvn install

If the `rpm` executable is found at `/usr/local/bin/rpm`, the build profile that includes the RPM 
packaging will be activated. If `rpm` is available, but at a different path, then activate it by using
Maven's `-P` switch: `mvn -Pprm install`.