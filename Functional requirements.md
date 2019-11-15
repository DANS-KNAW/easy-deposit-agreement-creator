# Functional Requirements Deposit Agreement Creator

## Background
At certain moments in time datasets require a new deposit agreement. This is mainly when a dataset is first
ingested (both via the front-end via `easy-webui` or via the backend using `easy-ingest` and
`easy-sword2`) or when something in the dataset's metadata changes (using one of the tools written
for specific tasks). `easy-deposit-agreement-creator` facilitates the generation of a deposit agreement according to a
given dataset, but does *not* ingest the agreement into the database.

### Former version
The main reason for creating this separate module is to replace the old agreement generator in the
business logic, as this version turns out to not be particularly usable in the various modules that
require the generation of a new deposit agreement. The old generator takes the *dataset*, *depositor data*
and an `OutputStream` to which the output is written as its arguments and returns `Unit`.

## New design
The new design of `easy-deposit-agreement-creator` consists of (1) an API which can be called from within other
modules which depend on this module and (2) a command line tool. In case the latter is used,
we assume that the dataset as well as the depositor data are already present in EASY. Again notice
that this command line tool is not intended to ingest the newly generated deposit agreement into EASY!

The input and output of both parts of `easy-deposit-agreement-creator` are as follows:
  * *input (via command line):* dataset identifier, output file location
  * *input (via API call):* either one of
      * dataset identifier, `OutputStream` - *used in modification tools*
      * EMD object, depositor object, `OutputStream` - *used in the business-layer*
      * EMD object, depositor identifier, an obsolete list of file metadata objects, `OutputStream` - *used in Easy-Stage-Dataset*
      * dataset object, `OutputStream`
  * *output (via command line):* pdf document with the deposit agreement
  * *output (via API call):* `Unit`

### Functionality
  * Generate a deposit agreement according to the dataset and the corresponding depositor data.
  * From the dataset mainly the EMD and the list of files are used.
  * From the depositor the contact information is used.
  * The interface of this module will comply with the current interface that is used in the business layer.

### Non-functionality
  * `easy-deposit-agreement-creator` will *not* add the newly created deposit agreement to EASY.
  * `easy-deposit-agreement-creator` wil *not* send emails to depositors whose datasets are modified or newly ingested.
  * No data is written to the databases; this module only reads data!

## Resources
The deposit agreement is generated from a series of template files with placeholders. Using the resources listed
below this module can resolve these placeholders and transform the whole text into a pdf.

### Template files
  * `styles.css` - styling for the various elements
  * `Agreement.html` - the main template with the content for the footer and parsing the other templates
  * `Body.html` - the main content of the deposit agreement text
  * `Appendix1.html` - an appendix with the chosen access rights, license and an optional embargo statement
  * `Appendix2.html` - an appendix with the dans license that may be applicable or not as explained in the body.html 
  * `dans_logo.png` - the Dans logo to be displayed in the header of each page
  * `DriveByData.png` - background in the footer
  * `MetadataTerms.properties` - a mapping between terms from the metadata and the text to be displayed in the agreement

### Data resources
  * *Fedora* - metadata of the dataset is stored in Fedora. The EMD datastream dissemination contains the metadata of the dataset itself, the AMD datastream dissemination contains the depositor identifier.
  * *LDAP* - the depositor data required for the agreement is stored in LDAP.

### Required data in the template
Besides the dataset's metadata and the list of files contained in the dataset, several other values
are required in the creation of the deposit agreement.

| Data | Used in | Stored in |
|------|---------|-----------|
| Dataset - identifier | all occasions where a query for (a part of) the dataset in Fedora is required | application parameter |
| Dataset - DANS managed DOI | template `Header.tml` | `emd:identifier // dc:identifier` |
| Dataset - encoded DANS managed DOI | template `Header.html`, see the link on the managed DOI above | `let id = emd:identifier // dc:identifier in (id@eas:identification-system ++ "/" ++ id.value)` |
| Dataset - date submitted | template `Header.tml` | `emd:date // eas:dateSubmitted` |
| Dataset - preferred title | template `Header.html` | `emd:title // dc:title` |
| Dataset - open access | template `Apppendix1.html` | `emd:rights // dct:accessRights` or `dc:rights` <br> (*these are always the same, only in different schemas. Therefore we can always use the value from EMD to get the least amount of Fedora queries*)|
| Dataset - is under embargo | template `Apppendix1.html` | to be calculated based on the current date and `Dataset - date available` below |
| Dataset - date available | template `Apppendix1.html` | `emd:date // eas:available` |
| EasyUser - displayName | template `Body.html` | LDAP user database - `(givenName <> initials)? + dansPrefixes? + sn?` |
| EasyUser - organization | template `Body.html` | LDAP user database - `o` |
| EasyUser - address | template `Body.html` | LDAP user database - `postalAddress` |
| EasyUser - postalCode | template `Body.html` | LDAP user database - `postalCode` |
| EasyUser - city | template `Body.html` | LDAP user database - `l` |
| EasyUser - country | template `Body.html` | LDAP user database - `st` |
| EasyUser - telephone | template `Body.html` | LDAP user database - `telephoneNumber` |
| EasyUser - email | template `Body.html` | LDAP user database - `mail` |

## Page layout
* The document has an A4 page size and the following margins (top-right-bottom-left): 2.5cm 1.5cm 2cm 1.5cm
* Every page has a header with the DANS logo
* Every page has a footer with the agreement's version number as well as the page number

## Pdf generation
The deposit agreement is generated from an html template and converted to pdf by
[Apache Velocity](http://velocity.apache.org/) and [WeasyPrint](http://weasyprint.org/) respectively.
See the README on installation notes for WeasyPrint. 

### Velocity
Velocity is a Java library and it supposed to be used as such! If a placeholder in the template
requires a list or map, it needs to be a `java.util.List` or `java.util.Map` instance. This requires
some extra attention as `easy-deposit-agreement-creator` itself is written in Scala.

Velocity does not complain or give an error message by default if certain placeholders cannot be
resolved. This only happens when the property `runtime.references.strict = true` is set in the
Velocity properties file. Besides that Velocity requires the path to the resources to be set using
the property `file.resource.loader.path`. As an extra parameter we added `template.file.name`,
holding the name of the file to be resolved by Velocity. This file is supposed to be present inside
the `file.resource.loader.path` folder. All these parameters are [hard coded](https://github.com/DANS-KNAW/easy-deposit-agreement-creator/blob/230a1e1ffaf24213f71277c1de1dbb9cd08daf96/src/main/scala/nl/knaw/dans/easy/agreement/internal/package.scala#L43-L51).

### WeasyPrint
The transformation from html to pdf is done using the WeasyPrint command line tool. This tool is
installed on the servers (*deasy*, *teasy* and *easy11*). For running it locally (during
development) we recommend using the `src/main/assembly/dist/res/pdfgen.sh` script. Indicate this in the
`application.properties` located in `src/main/assembly/dist/cfg/` and fill in the `...`
placeholders in the script.
