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

import com.yourmediashelf.fedora.client.request.RiSearch
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import com.yourmediashelf.fedora.generated.management.DatastreamProfile
import nl.knaw.dans.easy.agreement.DatasetID
import rx.lang.scala.Observable
import nl.knaw.dans.easy.agreement._

import scala.io.Source

trait Fedora {

  /**
    * Queries Fedora for the AMD datastream dissemination xml of the dataset with `identifier = pid`.
    *
    * @param pid identifier of the dataset to be queried
    * @return the resulting `InputStream` wrapped in an `Observable`
    */
  def getAMD(pid: DatasetID): Observable[InputStream]

  /**
    * Queries Fedora for the DC datastream dissemination xml of the dataset with `identifier = pid`.
    *
    * @param pid identifier of the dataset to be queried
    * @return the resulting `InputStream` wrapped in an `Observable`
    */
  def getDC(pid: DatasetID): Observable[InputStream]

  /**
    * Queries Fedora for the EMD datastream dissemination xml of the dataset with `identifier = pid`.
    *
    * @param pid identifier of the dataset to be queried
    * @return the resulting `InputStream` wrapped in an `Observable`
    */
  def getEMD(pid: DatasetID): Observable[InputStream]

  /**
    * Queries Fedora for the FILE_METADATA datastream dissemination xml of the dataset with `identifier = pid`.
    *
    * @param pid identifier of the dataset to be queried
    * @return the resulting `InputStream` wrapped in an `Observable`
    */
  def getFileMetadata(pid: FileID): Observable[InputStream]

  /**
    * Queries Fedora for the EASY_FILE datastream of the dataset with `identifier = pid`.
    *
    * @param pid identifier of the dataset to be queried
    * @return the resulting `DatastreamProfile` wrapped in an `Observable`
    */
  def getFile(pid: FileID): Observable[DatastreamProfile]

  /**
    * Executes a ``RiSearch`` query in Fedora.
    *
    * @param query the query to be executed
    * @return the result of that query
    */
  def queryRiSearch(query: String): Observable[String]
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

  def getDC(pid: DatasetID): Observable[InputStream] = query(pid, "DC").single

  def getEMD(pid: DatasetID): Observable[InputStream] = query(pid, "EMD").single

  def getFileMetadata(pid: FileID): Observable[InputStream] = query(pid, "EASY_FILE_METADATA").single

  def getFile(pid: FileID): Observable[DatastreamProfile] = {
    Observable.just(FedoraClient.getDatastream(pid, "EASY_FILE")
      .execute(client)
      .getDatastreamProfile)
  }

  def queryRiSearch(query: String): Observable[String] = {
    Observable.defer {
      new RiSearch(query).lang("sparql").format("csv").execute(client)
        .getEntityInputStream
        .usedIn(is => Observable.from(Source.fromInputStream(is)
          .getLines().toIterable).drop(1).map(_.split("/").last))
    }
  }
}
