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

import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import rx.lang.scala.Observable

import scala.language.postfixOps

case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)

object Dataset {
  def getDatasetByID(datasetID: DatasetID, userID: UserID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
    val emd = queryEMD(datasetID).single
    val user = EasyUser.getByID(userID).single

    emd.combineLatestWith(user)(Dataset(datasetID, _, _))
  }

  def getDatasetByID(datasetID: DatasetID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
    for {
      userID <- queryAMDForDepositorID(datasetID).single
      dataset <- getDatasetByID(datasetID, userID)
    } yield dataset
  }

  private def queryEMD(datasetID: DatasetID)(implicit client: FedoraClient): Observable[EasyMetadata] = {
    queryFedora(datasetID, "EMD")(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
  }

  private def queryAMDForDepositorID(datasetID: DatasetID)(implicit client: FedoraClient): Observable[UserID] = {
    queryFedora(datasetID, "AMD")(_.loadXML \\ "depositorId" text)
  }
}
