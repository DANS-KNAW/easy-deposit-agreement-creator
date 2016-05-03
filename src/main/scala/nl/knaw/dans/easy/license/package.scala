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

import java.io.{File, InputStream}
import java.util.Properties
import javax.naming.{Context, NamingEnumeration}
import javax.naming.directory.{Attributes, SearchControls}
import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import rx.lang.scala.Observable

import scala.language.postfixOps

package object license {

  type DatasetID = String
  type UserID = String

  val homeDir = new File(System.getProperty("app.home"))

  case class Parameters(fedora: FedoraCredentials, ldap: LdapContext, input: ConsoleInput) {
    override def toString: String = {

      s"""
        |Parameters {
        |  fedoraCredentials {
        |    url = ${fedora.getBaseUrl}
        |    user = ${fedora.getUsername}
        |  }
        |  ldap {
        |    url = ${ldap.getEnvironment.get(Context.PROVIDER_URL)}
        |    user = ${ldap.getEnvironment.get(Context.SECURITY_PRINCIPAL)}
        |  }
        |  ${input.toString.replaceAll("\n", "\n  ")}
        |}
      """.stripMargin
    }
  }

  case class ConsoleInput(userID: Option[UserID], datasetID: DatasetID, resultFile: File) {
    override def toString: String = {
      s"""input {
        |  userID = ${userID.getOrElse("<no userID entered>")}
        |  datasetID = $datasetID
        |  resultFile = ${resultFile.getAbsolutePath}
        |}""".stripMargin
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

  def queryFedora[T](datasetID: DatasetID, datastreamID: String)(f: InputStream => T)(implicit client: FedoraClient): Observable[T] = {
    Observable.just(FedoraClient.getDatastreamDissemination(datasetID, datastreamID).execute(client))
      .map(f compose (_.getEntityInputStream))
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
    }
  }
}
