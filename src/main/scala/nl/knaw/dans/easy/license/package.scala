/**
  * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
  *
  * Licensed under the Apache License, Version 2.0 (the "License");
  * you may not use this file except in compliance with the License.
  * You may obtain a copy of the License at
  *
  * http://www.apache.org/licenses/LICENSE-2.0
  *
  * Unless required by applicable law or agreed to in writing, software
  * distributed under the License is distributed on an "AS IS" BASIS,
  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
  * See the License for the specific language governing permissions and
  * limitations under the License.
  */
package nl.knaw.dans.easy

import java.io.{Closeable, File, InputStream}
import java.util.Properties
import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls}
import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import org.apache.commons.io.IOUtils
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.IOScheduler

import scala.language.postfixOps
import scala.xml.XML

package object license {

  type DatasetID = String
  type UserID = String

  case class Parameters(appHomeDir: File, fedora: FedoraCredentials, ldap: LdapContext,
                        input: ConsoleInput) {
    override def toString: String = s"Parameters($appHomeDir, $input)"
  }

  case class ConsoleInput(userID: Option[UserID], datasetID: DatasetID, resultFile: File) {
    override def toString: String = {
      s"ConsoleInput(${userID.getOrElse("<no userID>")}, $datasetID, $resultFile)"
    }
  }

  object Version {
    def apply(): String = {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
    }
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

    def closeQuietly() = IOUtils closeQuietly resource
  }

  implicit class InputStreamExtensions(val stream: InputStream) extends AnyVal {
    def loadXML = XML load stream
  }

  implicit class ObservableExtensions[T](val observable: Observable[T]) extends AnyVal {
    def debugThreadName(s: String = "") = observable.materialize.doOnEach(_ => println(s"$s: ${Thread.currentThread().getName}")).dematerialize
    def debug(s: String = "") = observable.materialize.doOnEach(x => println(s"$s: $x")).dematerialize
  }

  def queryFedora[T](datasetID: DatasetID, datastreamID: String)(f: InputStream => T)(implicit client: FedoraClient): Observable[T] = {
    FedoraClient.getDatastreamDissemination(datasetID, datastreamID)
      .execute(client)
      .getEntityInputStream
      .usedIn(f.andThen(Observable.just(_)))
      .subscribeOn(IOScheduler())
  }

  def queryLDAP[T](userID: UserID)(f: Attributes => T)(implicit ctx: LdapContext): Observable[T] = {
    Observable.defer {
      val searchFilter = s"(&(objectClass=easyUser)(uid=$userID))"
      val searchControls = {
        val sc = new SearchControls()
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
        sc
      }

      ctx.search("dc=dans,dc=knaw,dc=nl", searchFilter, searchControls)
        .toObservable
        .map(f compose (_.getAttributes))
        .subscribeOn(IOScheduler())
    }
  }
}
