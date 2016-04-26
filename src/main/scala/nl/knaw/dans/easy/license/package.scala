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

import java.io.InputStream
import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls}
import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import rx.lang.scala.Observable

import scala.language.postfixOps

package object license {

  case class Parameters(/* Insert parameters */) {
    override def toString: String =
      s"<Replace with nicely formatted string with name-value style output of parameters>"
  }

  object Version {
    def apply(): String = {
      val props = new java.util.Properties()
      props.load(Version.getClass.getResourceAsStream("/Version.properties"))
      props.getProperty("application.version")
    }
  }

  case class EasyUser(userID: String, name: String, organization: String, address: String,
                      postalCode: String, city: String, country: String, telephone: String,
                      email: String)

  object EasyUser {

    def getByID(userID: String)(implicit ctx: LdapContext): Observable[EasyUser] = {
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

  case class Dataset(emd: EasyMetadata, easyUser: EasyUser)

  object Dataset {
    def getDatasetByID(datasetID: String, userID: String)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
      val emd = queryEMD("easy-dataset:1").single
      val user = EasyUser.getByID(userID).single

      emd.combineLatestWith(user)(Dataset(_, _))
    }

    private def queryEMD(datasetID: String)(implicit client: FedoraClient): Observable[EasyMetadata] = {
      queryFedora(datasetID, "EMD")(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
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

  def queryFedora[T](datasetID: String, datastreamID: String)(f: InputStream => T)(implicit client: FedoraClient): Observable[T] = {
    Observable.just(FedoraClient.getDatastreamDissemination(datasetID, datastreamID).execute(client))
      .map(response => f(response.getEntityInputStream))
  }

  def queryLDAP[T](userID: String)(f: Attributes => T)(implicit ctx: LdapContext): Observable[T] = {
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
