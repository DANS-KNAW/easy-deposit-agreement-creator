# Functional Requirements License Creator

## Background
At certain moments in time datasets require a new license. This is mainly when a dataset is first ingested (both via the front-end via `WebUI` or via the backend using `EASY-Ingest` and `EASY-Deposit`) or when something in the dataset's metadata changes (using one of the tools written for specific tasks). EASY-License-Creator facilitates the generation of a license according to a given dataset, but does *not* ingest the license into the database.

### Former version
The main reason for creating this separate module is to replace the old license generator in the business logic, as this version turns out to not be particularly usable in the various modules that require the generation of a new license. The old generator takes the *dataset*, *depositor data* and an `OutputStream` to which the output is written as its arguments and returns `Unit`.

## New design
The new design of the License Creator consists of (1) an API which can be called from within other modules that are dependents of this module and (2) a command line tool. In case the latter is used, we assume that the dataset as well as the depositor data are already present in EASY. Again notice that this command line tool is not intended to ingest the newly generated license into EASY!

The input and output of both parts of the License Creator are as follows:
  * *input (via command line):* dataset identifier, output file location
  * *input (via API call):* either one of
      * dataset identifier, `OutputStream`
      * dataset object, `OutputStream`
      * TODO other forms of input
  * *output (via command line):* pdf document with the license agreement
  * *output (via API call):* `Unit`

### Functionality
  * Generate a license agreement according to the dataset and the corresponding depositor data.
  * From the dataset mainly the EMD and the list of files are used.
  * From the depositor the contact information is used.
  * The interface of this module will comply with the current interface that is used in the business layer.

### Non-functionality
  * The License Creator will *not* add the newly created license agreement to EASY.
  * The License Creator wil *not* send emails to depositors whose datasets are modified or newly ingested.
  * No data is written to the databases; this module only reads data!

### Additions relative to the former version
  * In the list of files in the dataset the access category for each file needs to be included.
  * An explaination of the distinct access categories from the previous item.

## Resources
The license is generated from a series of template files with placeholders. Using the resources listed below this module can resolve these placeholders and transform the whole text into a pdf.

### Template files
  * `Appendix.html` - an appendix with more information about the CC0 access category
  * `Body.html` - the main content of the license agreement text
  * `dans_logo.jpg` - the Dans logo to be displayed in the header of each page
  * `Embargo.html` - an optional text in case the dataset is under embargo
  * `FileTable.html` - an overview of all the files in the dataset, showing the file path, checksum and access category
  * `License.html` - the main file in which all the other html files are merged together
  * `LicenseVersion.txt` - the version of the license to be displayed in the footer of the license
  * `MetadataTerms.properties` - a mapping between terms from the metadata and the text to be displayed in the license
  * `Table.html` - an overview of the metadata of the dataset

### Data resources
  * *Fedora* - metadata of the dataset is stored in Fedora. The EMD datastream dissemination contains the metadata of the dataset itself, the AMD datastream dissemination contains the depositor identifier, the EASY_FILE datastream and EASY_FILE_METADATA datastream dissemination contain the data of the files in the dataset.
  * *LDAP* - the depositor data required for the license is stored in LDAP.
  * *RiSearch* - this is part of Fedora and provides the relation between the dataset and the files.

### Required data in the template
Besides the dataset's metadata and the list of files contained in the dataset, several other values are required in the creation of the license agreement.

| Data | Used in | Stored in |
|------|---------|-----------|
| Dataset - identifier | all occasions where a query for (a part of) the dataset in Fedora is required | application parameter |
| Dataset - DANS managed DOI | template `Body.html` | `emd:identifier // dc:identifier` |
| Dataset - encoded DANS managed DOI | template `Body.html`, see the link on the managed DOI above | `let id = emd:identifier // dc:identifier in (id@eas:identification-system ++ "/" ++ id.value)` |
| Dataset - date submitted | template `Body.html` | `emd:date // eas:dateSubmitted` |
| Dataset - preferred title | template `Body.html` | `emd:title // dc:title` |
| Dataset - access category | template `License.html` | `emd:rights // dct:accessRights` or `dc:rights` (*these are always the same, only in different schemas. Therefore we can always use the value from EMD to get the least amount of Fedora queries*)|
| Dataset - is under embargo | code `LicenseComposer.java:193` | to be calculated based on the current date and `Dataset - date available` below |
| Dataset - date available | template `Embargo.html` | `emd:date // eas:available` |
| Current time | template `Tail.html`, this is the timestamp of creating the license: `new org.joda.time.DateTime().toString("YYYY-MM-dd HH:mm:ss"))` | calculated at runtime |
| EasyUser - displayName | template `Body.html` | LDAP user database - `(givenName <> initials)? + dansPrefixes? + sn?` |
| EasyUser - organization | template `Body.html` | LDAP user database - `o` |
| EasyUser - address | template `Body.html` | LDAP user database - `postalAddress` |
| EasyUser - postalCode | template `Body.html` | LDAP user database - `postalCode` |
| EasyUser - city | template `Body.html` | LDAP user database - `l` |
| EasyUser - country | template `Body.html` | LDAP user database - `st` |
| EasyUser - telephone | template `Body.html` | LDAP user database - `telephoneNumber` |
| EasyUser - email | template `Body.html` | LDAP user database - `mail` |

### Displaying the dataset metadata
* For each term in the metadata the *qualified name* is calculated ([namespace].[name]) and mapped to the corresponding value in `MetadataTerms.properties`.
* If the term equals **AUDIENCE**, all associated *discipline identifiers* are queried in the [...] and displayed as a comma-separated `String`.
* If the term equals **ACCESSRIGHTS**, it is mapped to the corresponding string representation (see below).
* For all other terms the values are displayed as a comma-separated `String`.
* Every term corresponds to one row in the table.

### Displaying the files in the dataset
* All files contained in the dataset are retrieved from Fedora using `RiSearch`.
* For each file the SHA1-hash is queried.
* Each file (path and hash) corresponds to one row in the table.
* In case the dataset does not contain any files, the text "*No uploaded files*" is added instead of the table.
* In case the SHA1-hash of a file is not calculated, the alternative text "*------------- not-calculated -------------*" is used

### Mapping of access categories
| Access Category | License snippet | String representation |
|-----------------|-----------------|-----------------------|
| ANONYMOUS_ACCESS | OpenAccess.html | {{Anonymous}} |
| OPEN_ACCESS | OpenAccess.html | {color:#205081}*Open Access*{color} |
| OPEN_ACCESS_FOR_REGISTERED_USERS | OpenAccessForRegisteredUsers.html | {{Open access for registered users}} |
| GROUP_ACCESS | RestrictGroup.html | {{Restricted -'archaeology' group}} |
| REQUEST_PERMISSION | RestrictRequest.html | {{Restricted -request permission}} |
| ACCESS_ELSEWHERE | OtherAccess.html | {{Elsewhere}} |
| NO_ACCESS | OtherAccess.html | {{Other}} |
| FREELY_AVAILABLE | OpenAccess.html | Open Access |

