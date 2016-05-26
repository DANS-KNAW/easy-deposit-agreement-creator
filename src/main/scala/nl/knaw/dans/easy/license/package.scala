/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy

import java.io._
import java.nio.charset.Charset
import java.util.Properties
import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls}
import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.request.RiSearch
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import org.apache.commons.io.{Charsets, FileUtils, IOUtils}
import org.apache.commons.lang.StringUtils
import rx.lang.scala.Observable

import scala.io.Source
import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.xml.XML

package object license {

  type DatasetID = String
  type DepositorID = String
  type PlaceholderMap = Map[KeywordMapping, Object]

  val encoding = Charsets.UTF_8
  def velocityProperties(implicit parameters: Parameters) = {
    new File(parameters.templateDir, "/velocity-engine.properties")
  }
  def templateFile(implicit parameters: Parameters) = {
    new File(parameters.templateDir, "/License-template.html")
  }
  def dansLogoFile(implicit parameters: Parameters) = {
    new File(parameters.templateDir, "/dans_logo.jpg")
  }
  def footerTextFile(implicit parameters: Parameters) = {
    new File(parameters.templateDir, "/license_version.txt")
  }
  def metadataTermsProperties(implicit parameters: Parameters) = {
    new File(parameters.templateDir, "/MetadataTerms.properties")
  }

  case class Parameters(appHomeDir: File,
                        templateDir: File,
                        outputFile: File,
                        datasetID: DatasetID,
                        vagrant: VagrantConnection,
                        fedora: FedoraCredentials,
                        ldap: LdapContext) {
    override def toString: String = s"Parameters($appHomeDir, $templateDir, $outputFile, $datasetID, $vagrant)"
  }

  object Version {
    def apply(): String = {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
    }
  }

  implicit class FileExtensions(val file: File) extends AnyVal {
    /**
      * Copies a whole directory to a new location preserving the file dates.
      * <p>
      * This method copies the specified directory and all its child
      * directories and files to the specified destination.
      * The destination is the new location and name of the directory.
      * <p>
      * The destination directory is created if it does not exist.
      * If the destination directory did exist, then this method merges
      * the source with the destination, with the source taking precedence.
      * <p>
      * <strong>Note:</strong> This method tries to preserve the files' last
      * modified date/times using ``File#setLastModified(long)``, however
      * it is not guaranteed that those operations will succeed.
      * If the modification operation fails, no indication is provided.
      *
      * @param destDir the new directory, must not be ``null``
      * @throws NullPointerException if source or destination is ``null``
      * @throws IOException          if source or destination is invalid
      * @throws IOException          if an IO error occurs during copying
      */
    def copyDir(destDir: File) = FileUtils.copyDirectory(file, destDir)

    /**
      * Determines whether the ``parent`` directory contains the ``child`` element (a file or directory).
      * <p>
      * Files are normalized before comparison.
      * </p>
      *
      * Edge cases:
      * <ul>
      * <li>A ``directory`` must not be null: if null, throw IllegalArgumentException</li>
      * <li>A ``directory`` must be a directory: if not a directory, throw IllegalArgumentException</li>
      * <li>A directory does not contain itself: return false</li>
      * <li>A null child file is not contained in any parent: return false</li>
      * </ul>
      *
      * @param child the file to consider as the child.
      * @return true is the candidate leaf is under by the specified composite. False otherwise.
      * @throws IOException if an IO error occurs while checking the files.
      */
    def directoryContains(child: File): Boolean = FileUtils.directoryContains(file, child)

    /**
      * Deletes a directory recursively.
      *
      * @throws IOException in case deletion is unsuccessful
      */
    def deleteDirectory() = FileUtils.deleteDirectory(file)

    /**
      * Reads the contents of a file into a String using the default encoding for the VM.
      * The file is always closed.
      *
      * @return the file contents, never ``null``
      * @throws IOException in case of an I/O error
      */
    def read(encoding: Charset = Charsets.UTF_8) = FileUtils.readFileToString(file, encoding)
  }

  implicit class TryExtensions[T](val t: Try[T]) extends AnyVal {
    def doOnError(f: Throwable => Unit): Try[T] = {
      t match {
        case Failure(e) => Try { f(e); throw e }
        case x => x
      }
    }

    def doOnSuccess(f: T => Unit): Try[T] = {
      t match {
        case Success(x) => Try { f(x); x }
        case e => e
      }
    }

    def eventually[Ignore](effect: () => Ignore): Try[T] = {
      t.doOnSuccess(_ => effect()).doOnError(_ => effect())
    }

    def toObservable: Observable[T] = {
      t match {
        case Success(x) => Observable.just(x)
        case Failure(e) => Observable.error(e)
      }
    }
  }

  implicit class StringExtensions(val s: String) extends AnyVal {
    /**
      * Checks whether the `String` is blank
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]])
      *
      * @return
      */
    def isBlank = StringUtils.isBlank(s)

    /** Converts a `String` to an `Option[String]`. If the `String` is blank
      * (according to [[org.apache.commons.lang.StringUtils.isBlank]])
      * the empty `Option` is returned, otherwise the `String` is returned
      * wrapped in an `Option`.
      *
      * @return an `Option` of the input string that indicates whether it is blank
      */
    def toOption = if (s.isBlank) Option.empty else Option(s)
  }

  implicit class NamingEnumerationToObservable[T](val enum: NamingEnumeration[T]) extends AnyVal {
    def toObservable = Observable.from(new Iterable[T] {
      def iterator = new Iterator[T] {
        def hasNext = enum.hasMore

        def next() = enum.next()
      }
    })
  }

  implicit class CloseableResourceExtensions[T <: Closeable](val resource: T) extends AnyVal {
    def usedIn[S](observableFactory: T => Observable[S], dispose: T => Unit = _ => {}, disposeEagerly: Boolean = false): Observable[S] = {
      Observable.using(resource)(observableFactory, t => { dispose(t); t.closeQuietly() }, disposeEagerly)
    }

    def use[S](f: T => S, dispose: T => Unit = _ => {}): Try[S] = {
      Try(f(resource)).eventually(() => { dispose(resource); resource.closeQuietly() })
    }

    def closeQuietly() = IOUtils closeQuietly resource
  }

  implicit class InputStreamExtensions(val stream: InputStream) extends AnyVal {
    def loadXML = XML load stream
  }

  // TODO not used here anymore, but useful for debugging purposed. Maybe we can migrate this to a EASY-Utils project?
  implicit class ObservableDebug[T](val observable: Observable[T]) extends AnyVal {
    def debugThreadName(s: String = "") = observable.materialize.doOnEach(_ => println(s"$s: ${Thread.currentThread().getName}")).dematerialize
    def debug(s: String = "") = observable.materialize.doOnEach(x => println(s"$s: $x")).dematerialize
  }

  def queryFedora[T](datasetID: DatasetID, datastreamID: String)(f: InputStream => T)(implicit client: FedoraClient): Observable[T] = {
    FedoraClient.getDatastreamDissemination(datasetID, datastreamID)
      .execute(client)
      .getEntityInputStream
      .usedIn(f.andThen(Observable.just(_)))
  }

  def queryRiSearch[T](query: => String)(implicit client: FedoraClient) = {
    Observable.defer {
      new RiSearch(query).lang("sparql").format("csv").execute(client)
        .getEntityInputStream
        .usedIn(is => Observable.from(Source.fromInputStream(is)
          .getLines().toIterable).drop(1).map(_.split("/").last))
    }
  }

  def queryLDAP[T](depositorID: DepositorID)(f: Attributes => T)(implicit ctx: LdapContext): Observable[T] = {
    Observable.defer {
      val searchFilter = s"(&(objectClass=easyUser)(uid=$depositorID))"
      val searchControls = {
        val sc = new SearchControls()
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
        sc
      }

      ctx.search("dc=dans,dc=knaw,dc=nl", searchFilter, searchControls)
        .toObservable
        .map(f compose (_.getAttributes))
    }
  }

  def loadProperties(file: File): Try[Properties] = {
    new FileInputStream(file)
      .use(fis => {
        val props = new Properties
        props.load(fis)
        props
      })
  }
}
