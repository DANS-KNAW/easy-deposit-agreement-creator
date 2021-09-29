/*
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
package nl.knaw.dans.easy.agreement.datafetch

import java.io.InputStream

import com.yourmediashelf.fedora.client.{ FedoraClient, FedoraClientException }
import nl.knaw.dans.easy.agreement.{ DatasetId, FedoraUnavailableException, NoDatasetFoundException }

import scala.util.{ Failure, Try }

trait Fedora {

  /**
   * Queries Fedora for the AMD datastream dissemination xml of the dataset with `identifier = pid`.
   *
   * @param datasetId identifier of the dataset to be queried
   * @return the resulting `InputStream`
   */
  def getAMD(datasetId: DatasetId): Try[InputStream]

  /**
   * Queries Fedora for the EMD datastream dissemination xml of the dataset with `identifier = pid`.
   *
   * @param datasetId identifier of the dataset to be queried
   * @return the resulting `InputStream`
   */
  def getEMD(datasetId: DatasetId): Try[InputStream]

  /**
   * Queries whether the provided datasetId exists in Fedora
   *
   * @param datasetId identifier of the dataset to be queried
   * @return true if the dataset exist else false
   */
  def datasetIdExists(datasetId: DatasetId): Try[Boolean]
}

class FedoraImpl(client: FedoraClient) extends Fedora {

  private def query(pid: String, datastreamID: String): Try[InputStream] = Try {
    FedoraClient.getDatastreamDissemination(pid, datastreamID)
      .execute(client)
      .getEntityInputStream
  } recoverWith {
    case e: FedoraClientException if e.getStatus >= 400 && e.getStatus < 500 => Failure(NoDatasetFoundException(pid, e))
    case e: FedoraClientException => Failure(FedoraUnavailableException(pid, e))
  }

  override def getAMD(pid: DatasetId): Try[InputStream] = query(pid, "AMD")

  override def getEMD(pid: DatasetId): Try[InputStream] = query(pid, "EMD")

  override def datasetIdExists(datasetId: DatasetId): Try[Boolean] = Try {
    FedoraClient.getObjectXML(datasetId)
      .execute(client)
      .getStatus == 200
  } recover { case _: FedoraClientException => false }
}
