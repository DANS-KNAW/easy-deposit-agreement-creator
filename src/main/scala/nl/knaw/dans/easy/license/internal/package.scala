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
package nl.knaw.dans.easy.license

import java.io._
import java.nio.charset.Charset
import java.util.Properties

import org.apache.commons.io.{Charsets, FileUtils, IOUtils}
import org.apache.commons.lang.StringUtils
import org.slf4j.Logger
import rx.lang.scala.Notification.{OnCompleted, OnError, OnNext}
import rx.lang.scala.{Notification, Observable}

import scala.language.postfixOps
import scala.util.{Failure, Success, Try}
import scala.xml.{Elem, XML}

package object internal {

  type AudienceID = String
  type AudienceTitle = String

  type FileID = String

  type PlaceholderMap = Map[KeywordMapping, Object]

  val encoding = Charsets.UTF_8
  val checkSumNotCalculated = "------not-calculated------"

  def velocityProperties(implicit parameters: BaseParameters): Properties = {
    val p = new Properties
    p.setProperty("runtime.references.strict", "true")
    p.setProperty("file.resource.loader.path", templateDir.getAbsolutePath)
    p.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute")
    p.setProperty("template.file.name", "License.html")

    p
  }

  def templateDir(implicit parameters: BaseParameters): File = {
    new File(parameters.templateResourceDir, "/template")
  }

  def dansLogoFile(implicit parameters: BaseParameters): File = {
    new File(parameters.templateResourceDir, "/dans_logo.png")
  }

  def footerTextFile(implicit parameters: BaseParameters): File = {
    new File(parameters.templateResourceDir, "/license_version.txt")
  }

  def metadataTermsProperties(implicit parameters: BaseParameters): File = {
    new File(parameters.templateResourceDir, "/MetadataTerms.properties")
  }

  def pdfRunScript(implicit parameters: BaseParameters): File = {
    new File(parameters.templateResourceDir, "/pdfgen.sh")
  }

  case class MultipleDatasetsFoundException(datasetID: DatasetID, cause: Exception) extends Exception(s"Found more than one dataset with id: '$datasetID'", cause)
  case class NoDatasetFoundException(datasetID: DatasetID, cause: Exception) extends Exception(s"Could not find dataset with id: '$datasetID", cause)
  case class MultipleUsersFoundException(depositorID: DepositorID, cause: Exception) extends Exception(s"Found more than one depositor with id: '$depositorID'", cause)
  case class NoUserFoundException(depositorID: DepositorID, cause: Exception) extends Exception(s"Could not find depositor with id: '$depositorID'", cause)

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
      */
    def copyDir(destDir: File): Unit = FileUtils.copyDirectory(file, destDir)

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
      */
    def directoryContains(child: File): Boolean = FileUtils.directoryContains(file, child)

    /**
      * Deletes a directory recursively.
      */
    def deleteDirectory(): Unit = FileUtils.deleteDirectory(file)

    /**
      * Reads the contents of a file into a String using the default encoding for the VM.
      * The file is always closed.
      *
      * @return the file contents, never ``null``
      */
    def read(encoding: Charset = Charsets.UTF_8): String = FileUtils.readFileToString(file, encoding)
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
      *
      * @return
      */
    def isBlank: Boolean = StringUtils.isBlank(s)

    /** Converts a `String` to an `Option[String]`. If the `String` is blank
      * the empty `Option` is returned, otherwise the `String` is returned
      * wrapped in an `Option`.
      *
      * @return an `Option` of the input string that indicates whether it is blank
      */
    def toOption: Option[String] = if (s.isBlank) Option.empty else Option(s)

    def emptyIfBlank: String = s.toOption.getOrElse("")
  }

  implicit class ReactiveResourceManager[T <: Closeable](val resource: T) extends AnyVal {
    def usedIn[S](observableFactory: T => Observable[S], dispose: T => Unit = _ => {}, disposeEagerly: Boolean = false): Observable[S] = {
      Observable.using(resource)(observableFactory, t => { dispose(t); IOUtils.closeQuietly(t) }, disposeEagerly)
    }
  }

  implicit class InputStreamExtensions(val stream: InputStream) extends AnyVal {
    def loadXML: Elem = XML.load(stream)
  }

  // TODO not used here anymore, but useful for debugging purposed. Maybe we can migrate this to a EASY-Utils project?
  implicit class ObservableDebug[T](val observable: Observable[T]) extends AnyVal {
    def debugThreadName(s: String = "")(implicit logger: Logger): Observable[T] = {

      def notificationKind(notification: Notification[T]) = {
        notification match {
          case OnNext(_) => "OnNext"
          case OnError(_) => "OnError"
          case OnCompleted => "OnCompleted"
        }
      }

      observable.materialize
        .doOnEach(o => logger.debug(s"$s: ${notificationKind(o)} - ${Thread.currentThread().getName}"))
        .dematerialize
    }

    def debug(s: String = "")(implicit logger: Logger): Observable[T] = {
      observable.materialize
        .doOnEach(x => logger.debug(s"$s: $x"))
        .dematerialize
    }
  }

  def loadProperties(file: File): Try[Properties] = {
    resource.Using.fileInputStream(file)
      .map(fis => {
        val props = new Properties
        props.load(fis)
        props
      })
      .tried
  }
}
