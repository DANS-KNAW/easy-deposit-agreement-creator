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
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import rx.lang.scala.Observable

import scala.language.postfixOps
import scala.xml.XML

package object license {

  type DatasetID = String
  type UserID = String

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

  object Version {
    def apply(): String = {
      val props = new Properties()
      props.load(getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
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

  case class EasyUser(userID: UserID, name: String, organization: String, address: String,
                      postalCode: String, city: String, country: String, telephone: String,
                      email: String)
  object EasyUser {

    def getByID(userID: UserID)(implicit ctx: LdapContext): Observable[EasyUser] = {
      queryLDAP(userID)(attrs => {
        def get(attrID: String): Option[String] = {
          Option(attrs get attrID) map (_ get) map (_ toString)
        }

        def getOrEmpty(attrID: String): String = get(attrID) getOrElse ""

        val name = getOrEmpty("displayname")
        val org = getOrEmpty("o")
        val addr = getOrEmpty("postaladdress")
        val code = getOrEmpty("postalcode")
        val place = getOrEmpty("l")
        val country = getOrEmpty("st")
        val phone = getOrEmpty("telephonenumber")
        val mail = getOrEmpty("mail")

        EasyUser(userID, name, org, addr, code, place, country, phone, mail)
      })
    }
  }

  case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)
  object Dataset {
    def getDatasetByID(datasetID: DatasetID, userID: UserID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
      val emd = queryEMD(datasetID).single
      val user = EasyUser.getByID(userID).single

      emd.combineLatestWith(user)(Dataset(datasetID, _, _))
    }

    def getDatasetByID(datasetID: DatasetID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
      queryAMDForDepositorID(datasetID)
        .single
        .flatMap(getDatasetByID(datasetID, _))
    }

    private def queryEMD(datasetID: DatasetID)(implicit client: FedoraClient): Observable[EasyMetadata] = {
      queryFedora(datasetID, "EMD")(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
    }

    private def queryAMDForDepositorID(datasetID: DatasetID)(implicit client: FedoraClient): Observable[UserID] = {
      queryFedora(datasetID, "AMD")(stream => XML.load(stream) \\ "depositorID" text)
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
      .map(response => f(response.getEntityInputStream))
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
        .map(result => f(result.getAttributes))
    }
  }
}
