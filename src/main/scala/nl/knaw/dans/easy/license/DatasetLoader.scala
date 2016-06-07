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

import javax.naming.directory.Attributes

import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.IOScheduler

import scala.language.postfixOps

case class EasyUser(userID: DepositorID, name: String, organization: String, address: String,
                    postalCode: String, city: String, country: String, telephone: String,
                    email: String)

case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)

trait DatasetLoader {

  def getDatasetById(datasetID: DatasetID): Observable[Dataset]

  def getUserById(depositorID: DepositorID): Observable[EasyUser]
}

case class DatasetLoaderImpl(implicit parameters: Parameters) extends DatasetLoader {

  val fedora = parameters.fedora
  val ldap = parameters.ldap

  def getDatasetById(datasetID: DatasetID) = {
    val depositor = fedora.getAMD(datasetID)(_.loadXML \\ "depositorId" text)
      .subscribeOn(IOScheduler())
      .flatMap(getUserById(_).subscribeOn(IOScheduler()))

    fedora.getEMD(datasetID)(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
      .subscribeOn(IOScheduler())
      .combineLatestWith(depositor)(Dataset(datasetID, _, _))
      .single
  }

  private def get(attrID: String)(implicit attrs: Attributes): Option[String] = {
    Option(attrs get attrID).map(_.get.toString)
  }

  private def getOrEmpty(attrID: String)(implicit attrs: Attributes) = {
    get(attrID) getOrElse ""
  }

  def getUserById(depositorID: DepositorID): Observable[EasyUser] = {
    ldap.query(depositorID)(implicit attrs => {
      val name = getOrEmpty("displayname")
      val org = getOrEmpty("o")
      val addr = getOrEmpty("postaladdress")
      val code = getOrEmpty("postalcode")
      val place = getOrEmpty("l")
      val country = getOrEmpty("st")
      val phone = getOrEmpty("telephonenumber")
      val mail = getOrEmpty("mail")


      EasyUser(depositorID, name, org, addr, code, place, country, phone, mail)
    }).single
  }
}

