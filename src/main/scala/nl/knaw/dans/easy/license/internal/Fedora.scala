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
package nl.knaw.dans.easy.license.internal

import java.io.InputStream

import com.yourmediashelf.fedora.client.request.RiSearch
import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import com.yourmediashelf.fedora.generated.management.DatastreamProfile
import rx.lang.scala.Observable

import scala.io.Source

trait Fedora {

  /**
    * Queries Fedora for the AMD datastream dissemination xml of the dataset with `identifier = pid`
    * and transforms the result of that query into an instance of type `T` using the function `f`.
    *
    * After the data is read from the `InputStream` this function will take care of closing this resource.
    *
    * @param pid identifier of the dataset to be queried
    * @param f function that transforms an `InputStream` of xml to an instance of type `T`
    * @tparam T the result type of the transformer function
    * @return the instance of `T` wrapped in an `Observable`
    */
  def getAMD[T](pid: DatasetID)(f: InputStream => T): Observable[T]

  /**
    * Queries Fedora for the DC datastream dissemination xml of the dataset with `identifier = pid`
    * and transforms the result of that query into an instance of type `T` using the function `f`.
    *
    * After the data is read from the `InputStream` this function will take care of closing this resource.
    *
    * @param pid identifier of the dataset to be queried
    * @param f function that transforms an `InputStream` of xml to an instance of type `T`
    * @tparam T the result type of the transformer function
    * @return the instance of `T` wrapped in an `Observable`
    */
  def getDC[T](pid: DatasetID)(f: InputStream => T): Observable[T]

  /**
    * Queries Fedora for the EMD datastream dissemination xml of the dataset with `identifier = pid`
    * and transforms the result of that query into an instance of type `T` using the function `f`.
    *
    * After the data is read from the `InputStream` this function will take care of closing this resource.
    *
    * @param pid identifier of the dataset to be queried
    * @param f function that transforms an `InputStream` of xml to an instance of type `T`
    * @tparam T the result type of the transformer function
    * @return the instance of `T` wrapped in an `Observable`
    */
  def getEMD[T](pid: DatasetID)(f: InputStream => T): Observable[T]

  /**
    * Queries Fedora for the FILE_METADATA datastream dissemination xml of the dataset with `identifier = pid`
    * and transforms the result of that query into an instance of type `T` using the function `f`.
    *
    * After the data is read from the `InputStream` this function will take care of closing this resource.
    *
    * @param pid identifier of the dataset to be queried
    * @param f function that transforms an `InputStream` of xml to an instance of type `T`
    * @tparam T the result type of the transformer function
    * @return the instance of `T` wrapped in an `Observable`
    */
  def getFileMetadata[T](pid: FileID)(f: InputStream => T): Observable[T]

  /**
    * Queries Fedora for the EASY_FILE datastream of the dataset with `identifier = pid`
    * and transforms the result of that query (an `DatastreamProfile` object) into an instance of
    * type `T` using the function `f`.
    *
    * @param pid identifier of the dataset to be queried
    * @param f function that transforms an `DatastreamProfile` to an instance of type `T`
    * @tparam T the result type of the transformer function
    * @return the instance of `T` wrapped in an `Observable`
    */
  def getFile[T](pid: FileID)(f: DatastreamProfile => T): Observable[T]

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

  private def query[T](pid: String, datastreamID: String)(f: InputStream => T): Observable[T] = {
    FedoraClient.getDatastreamDissemination(pid, datastreamID)
      .execute(client)
      .getEntityInputStream
      .usedIn(f.andThen(Observable.just(_)))
  }

  def getAMD[T](pid: DatasetID)(f: (InputStream) => T) = query(pid, "AMD")(f).single

  def getDC[T](pid: DatasetID)(f: (InputStream) => T) = query(pid, "DC")(f).single

  def getEMD[T](pid: DatasetID)(f: (InputStream) => T) = query(pid, "EMD")(f).single

  def getFileMetadata[T](pid: FileID)(f: (InputStream) => T) = query(pid, "EASY_FILE_METADATA")(f).single

  def getFile[T](pid: FileID)(f: DatastreamProfile => T): Observable[T] = {
    Observable.just(FedoraClient.getDatastream(pid, "EASY_FILE")
      .execute(client)
      .getDatastreamProfile)
      .map(f)
  }

  def queryRiSearch(query: String) = {
    Observable.defer {
      new RiSearch(query).lang("sparql").format("csv").execute(client)
        .getEntityInputStream
        .usedIn(is => Observable.from(Source.fromInputStream(is)
          .getLines().toIterable).drop(1).map(_.split("/").last))
    }
  }
}
