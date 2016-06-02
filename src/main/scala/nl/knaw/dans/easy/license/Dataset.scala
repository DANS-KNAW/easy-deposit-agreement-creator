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

import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import rx.lang.scala.Observable

import scala.language.postfixOps

case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)

trait DatasetLoader {

  def getDatasetByID(datasetID: DatasetID): Observable[Dataset]
}

case class DatasetLoaderImpl(implicit parameters: Parameters) extends DatasetLoader {

  val fedora = parameters.fedora
  val ldap = parameters.ldap

  def getDatasetByID(datasetID: DatasetID) = {
    fedora.getAMD(datasetID)(_.loadXML \\ "depositorId" text)
      .flatMap(depositorId => {
        val emd = fedora.getEMD(datasetID)(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
        val depositor = ldap.getUserById(depositorId)

        emd.combineLatestWith(depositor)(Dataset(datasetID, _, _))
      })
  }
}

