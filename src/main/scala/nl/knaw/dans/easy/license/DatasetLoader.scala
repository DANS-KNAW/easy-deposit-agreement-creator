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

import nl.knaw.dans.easy.license.FileAccessRight.FileAccessRight
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl, EmdAudience}
import rx.lang.scala.Observable
import rx.lang.scala.ObservableExtensions
import rx.lang.scala.schedulers.IOScheduler

import scala.collection.JavaConverters._
import scala.language.postfixOps

case class EasyUser(userID: DepositorID, name: String, organization: String, address: String,
                    postalCode: String, city: String, country: String, telephone: String,
                    email: String)

case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)

case class FileItem(filePid: String, path: String, accessibleTo: FileAccessRight, checkSum: String)

trait DatasetLoader {

  def getAudiences(audience: EmdAudience): Observable[Audience]

  def getDatasetById(datasetID: DatasetID): Observable[Dataset]

  def getFilesInDataset(datasetID: DatasetID): Observable[FileItem]

  def getUserById(depositorID: DepositorID): Observable[EasyUser]
}

case class DatasetLoaderImpl(implicit parameters: Parameters) extends DatasetLoader {

  val fedora = parameters.fedora
  val ldap = parameters.ldap

  def getAudiences(audience: EmdAudience) = {
    audience.getValues.asScala.toObservable
      .flatMap(sid => fedora.getDC(sid)(_.loadXML \\ "title" text).subscribeOn(IOScheduler()))
  }

  def getDatasetById(datasetID: DatasetID) = {
    val emd = fedora.getEMD(datasetID)(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
      .subscribeOn(IOScheduler())
    val depositorObs = fedora.getAMD(datasetID)(_.loadXML \\ "depositorId" text)
      .subscribeOn(IOScheduler())
      .flatMap(getUserById(_).subscribeOn(IOScheduler()))

    emd.combineLatestWith(depositorObs)(Dataset(datasetID, _, _))
      .single
  }

  private def getFileItem(filePid: String): Observable[FileItem] = {
    val pathAndAccessCategory = fedora.getFileMetadata(filePid)(is => {
      val xml = is.loadXML
      (xml \\ "path" text,
        FileAccessRight.valueOf(xml \\ "accessibleTo" text)
          .getOrElse(throw new IllegalArgumentException(s"illegal value for accessibleTo in file: $filePid")))
    }).subscribeOn(IOScheduler())

    val checksums = fedora.getFile(filePid)(_.getDsChecksum).subscribeOn(IOScheduler())

    pathAndAccessCategory
      .combineLatestWith(checksums) {
        case ((p, ac), cs) => FileItem(filePid, p, ac, cs)
      }
  }

  def getFilesInDataset(datasetID: DatasetID): Observable[FileItem] = {
    val query = "PREFIX dans: <http://dans.knaw.nl/ontologies/relations#> " +
      "PREFIX fmodel: <info:fedora/fedora-system:def/model#> " +
      s"SELECT ?s WHERE {?s dans:isSubordinateTo <info:fedora/$datasetID> . " +
      "?s fmodel:hasModel <info:fedora/easy-model:EDM1FILE>}"

    fedora.queryRiSearch(query)
      .subscribeOn(IOScheduler())
      .flatMap(getFileItem)
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

