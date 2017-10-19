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
package nl.knaw.dans.easy.license.internal

import java.sql.SQLException
import javax.naming.directory.Attributes

import nl.knaw.dans.easy.license.{ DatasetID, DepositorID, FileAccessRight, FileItem }
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import nl.knaw.dans.pf.language.emd.{ EasyMetadata, EasyMetadataImpl, EmdAudience }
import rx.lang.scala.schedulers.IOScheduler
import rx.lang.scala.{ Observable, ObservableExtensions }

import scala.collection.JavaConverters._
import scala.language.postfixOps
import scala.util.control.NonFatal

/**
  * Data class for an Easy User. Notice that some fields are mandatory and cannot be null!
  *
  * @param name the user's name <b>(mandatory!)</b>
  * @param organization the user's organisation
  * @param address the user's address <b>(mandatory!)</b>
  * @param postalCode the user's zipcode <b>(mandatory!)</b>
  * @param city the user's city <b>(mandatory!)</b>
  * @param country the user's country
  * @param telephone the user's telephone
  * @param email the user's email <b>(mandatory!)</b>
  */
case class EasyUser(name: String,
                    organization: String,
                    address: String,
                    postalCode: String,
                    city: String,
                    country: String,
                    telephone: String,
                    email: String) {

  require(name != null, "'name' must be defined")
  require(address != null, "'address' must be defined")
  require(postalCode != null, "'postalCode' must be defined")
  require(city != null, "'city' must be defined")
  require(email != null, "'email' must be defined")
}

case class Dataset(datasetID: DatasetID,
                   emd: EasyMetadata,
                   easyUser: EasyUser,
                   audiences: Seq[AudienceTitle],
                   fileItems: Seq[FileItem],
                   filesLimited: Boolean) {

  require(datasetID != null, "'datasetID' must be defined")

  def validate: Dataset = DatasetValidator.validate(this)
}

trait DatasetLoader {

  /**
    * Queries the audience title from Fedora given an audience identifiers
    *
    * @param audienceID the identifier of the audience
    * @return The audidence title corresponding to the `audienceID`
    */
  def getAudience(audienceID: AudienceID): Observable[AudienceTitle]

  /**
    * Queries the audience titles from Fedora for the audiences in `EmdAudience`.
    * @param audience the audience object with the identifiers
    * @return the titles of the audiences that correspond to the identifiers in `EmdAudience`
    */
  def getAudiences(audience: EmdAudience): Observable[AudienceTitle] = {
    audience.getValues.asScala.toObservable.flatMap(getAudience)
  }

  /**
    * Create a `Dataset` based on the given `datasetID`
    *
    * @param datasetID the identifier of the dataset
    * @return the dataset corresponding to `datasetID`
    */
  def getDatasetById(datasetID: DatasetID): Observable[Dataset]

  /**
    * Create a `Dataset` based on the given `datasetID`, `emd`, `depositorID` and `files`, while
    * querying for the audience titles and the depositor data.
    *
    * @param datasetID the identifier of the dataset
    * @param emd the `EasyMetadata` of the dataset
    * @param depositorID the depositor's identifier
    * @param files the files belonging to the dataset
    * @return the dataset corresponding to `datasetID`
    */
  def getDataset(datasetID: DatasetID, emd: EasyMetadata, depositorID: DepositorID, files: Seq[FileItem], fileLimit: Int): Observable[Dataset] = {
    val audiences = getAudiences(emd.getEmdAudience).toSeq
    val easyUser = getUserById(depositorID)
    val filesWereLimited = countFiles(datasetID).map(fileLimit <)

    easyUser.combineLatest(audiences)
      .combineLatestWith(filesWereLimited) {
        case ((user, auds), limited) => Dataset(datasetID, emd, user, auds, files, limited)
      }
  }

  /**
    * Returns all files corresponding to the dataset with identifier `datasetID`
    *
    * @param datasetID the identifier of the dataset
    * @return the files corresponding to the dataset with identifier `datasetID`
    */
  def getFilesInDataset(datasetID: DatasetID): Observable[FileItem]

  /**
   * Return the number of files corresponding to the dataset with identifier `datasetID`
   *
   * @param datasetID the identifier of the dataset
   * @return the number of files corresponding to the dataset with identifier `datasetID`
   */
  def countFiles(datasetID: DatasetID): Observable[Int]

  /**
    * Queries the user data given a `depositorID`
    *
    * @param depositorID the identifier of the user
    * @return the user data corresponding to the `depositorID`
    */
  def getUserById(depositorID: DepositorID): Observable[EasyUser]
}

case class DatasetLoaderImpl(implicit parameters: DatabaseParameters) extends DatasetLoader {

  def getAudience(audienceID: AudienceID): Observable[String] = {
    parameters.fedora.getDC(audienceID)
      .map(resource.managed(_).acquireAndGet(_.loadXML \\ "title" text))
      .subscribeOn(IOScheduler())
  }

  def getDatasetById(datasetID: DatasetID): Observable[Dataset] = {
    val emdObs = parameters.fedora.getEMD(datasetID)
      .map(resource.managed(_).acquireAndGet(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal))
      .subscribeOn(IOScheduler())
    val depositorObs = parameters.fedora.getAMD(datasetID)
      .map(resource.managed(_).acquireAndGet(_.loadXML \\ "depositorId" text))
      .subscribeOn(IOScheduler())
      .flatMap(getUserById(_).subscribeOn(IOScheduler()))

    // publish because emd is used in multiple places here
    emdObs.publish(emd => {
      emd.combineLatestWith(depositorObs) {
        (emdValue, depositorValue) => (audiences: Seq[AudienceTitle]) => (files: Seq[FileItem]) => (limited: Boolean) =>
          Dataset(datasetID, emdValue, depositorValue, audiences, files, limited)
      }
        .combineLatestWith(emd.map(_.getEmdAudience).flatMap(getAudiences).toSeq)(_(_))
        .combineLatestWith(getFilesInDataset(datasetID).toSeq)(_(_))
        .combineLatestWith(countFiles(datasetID).map(parameters.fileLimit <))(_(_))
        .single
        .onErrorResumeNext {
          case e: IllegalArgumentException => Observable.error(MultipleDatasetsFoundException(datasetID, e))
          case e: NoSuchElementException => Observable.error(NoDatasetFoundException(datasetID, e))
          case NonFatal(e) => Observable.error(e)
        }
    })
  }

  def getFilesInDataset(datasetID: DatasetID): Observable[FileItem] = {
    Class.forName("org.postgresql.Driver")
    val query = "SELECT pid, path, sha1checksum, accessible_to FROM easy_files WHERE dataset_sid = ? ORDER BY pid LIMIT ?;"
    Observable.using(parameters.fsrdb.prepareStatement(query))(
      prepStatement => {
        prepStatement.setString(1, datasetID)
        prepStatement.setInt(2, parameters.fileLimit)

        Observable.using(prepStatement.executeQuery())(
          resultSet => Observable.defer(Observable.just(resultSet.next()))
            .repeat
            .takeWhile(b => b)
            .map(_ => {
              val pid = resultSet.getString("pid")
              val path = resultSet.getString("path")
              val sha1checksum = resultSet.getString("sha1checksum")
              val accessibleTo = FileAccessRight.valueOf(resultSet.getString("accessible_to"))
                .getOrElse(throw new IllegalArgumentException(s"illegal value for accessibleTo in file: $pid"))

              // TODO make checksum optional
              FileItem(path, accessibleTo, if (sha1checksum == "null") null else sha1checksum)
            }),
          _.close(),
          disposeEagerly = true
        )
      },
      _.close(),
      disposeEagerly = true
    )
  }

  def countFiles(datasetID: DatasetID): Observable[Int] = {
    Class.forName("org.postgresql.Driver")
    val query = "SELECT COUNT(pid) FROM easy_files WHERE dataset_sid = ?;"
    Observable.using(parameters.fsrdb.prepareStatement(query))(
      prepStatement => {
        prepStatement.setString(1, datasetID)

        Observable.using(prepStatement.executeQuery())(
          resultSet => {
            if (resultSet.next())
              Observable.just(resultSet.getInt("count"))
            else
              Observable.error(new SQLException(s"unable to count the number of files in dataset $datasetID"))
          },
          _.close(),
          disposeEagerly = true
        )
      },
      _.close(),
      disposeEagerly = true
    )
  }

  private def get(attrID: String)(implicit attrs: Attributes): Option[String] = {
    Option(attrs get attrID).map(_.get.toString)
  }

  private def getOrEmpty(attrID: String)(implicit attrs: Attributes) = {
    get(attrID) getOrElse ""
  }

  def getUserById(depositorID: DepositorID): Observable[EasyUser] = {
    parameters.ldap
      .query(depositorID)
      .map(implicit attrs => {
        val name = getOrEmpty("displayname")
        val org = getOrEmpty("o")
        val addr = getOrEmpty("postaladdress")
        val code = getOrEmpty("postalcode")
        val place = getOrEmpty("l")
        val country = getOrEmpty("st")
        val phone = getOrEmpty("telephonenumber")
        val mail = getOrEmpty("mail")

        EasyUser(name, org, addr, code, place, country, phone, mail)
      })
      .single
      .onErrorResumeNext {
        case e: IllegalArgumentException => Observable.error(MultipleUsersFoundException(depositorID, e))
        case e: NoSuchElementException => Observable.error(NoUserFoundException(depositorID, e))
        case NonFatal(e) => Observable.error(e)
      }
  }
}

