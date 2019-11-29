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
package nl.knaw.dans.easy.agreement.internal

import java.io.InputStream

import com.yourmediashelf.fedora.client.{ FedoraClient, FedoraClientException, FedoraCredentials }
import nl.knaw.dans.easy.agreement.DatasetID
import rx.lang.scala.Observable

import scala.util.Try

trait Fedora {

  /**
   * Queries Fedora for the AMD datastream dissemination xml of the dataset with `identifier = pid`.
   *
   * @param pid identifier of the dataset to be queried
   * @return the resulting `InputStream` wrapped in an `Observable`
   */
  def getAMD(pid: DatasetID): Observable[InputStream]

  /**
   * Queries Fedora for the EMD datastream dissemination xml of the dataset with `identifier = pid`.
   *
   * @param pid identifier of the dataset to be queried
   * @return the resulting `InputStream` wrapped in an `Observable`
   */
  def getEMD(pid: DatasetID): Observable[InputStream]

  /**
   * Queries whether the provided datasetID exists in Fedora
   *
   * @param datasetID
   * @return true if the dataset exist else false
   */
  def datasetIdExists(datasetID: DatasetID): Try[Boolean]
}

case class FedoraImpl(client: FedoraClient) extends Fedora {

  def this(credentials: FedoraCredentials) = this(new FedoraClient(credentials))

  private def query(pid: String, datastreamID: String): Observable[InputStream] = {
    Observable.just {
      FedoraClient.getDatastreamDissemination(pid, datastreamID)
        .execute(client)
        .getEntityInputStream
    }
  }

  def getAMD(pid: DatasetID): Observable[InputStream] = query(pid, "AMD").single

  def getEMD(pid: DatasetID): Observable[InputStream] = query(pid, "EMD").single

  override def datasetIdExists(datasetID: DatasetID): Try[Boolean] = Try {
    FedoraClient.getObjectXML(datasetID)
      .execute(client)
      .getStatus == 200
  } recover { case _: FedoraClientException => false }
}
