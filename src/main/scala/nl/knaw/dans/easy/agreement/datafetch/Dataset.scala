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

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.common.lang.dataset.AccessCategory.{ OPEN_ACCESS, _ }
import nl.knaw.dans.easy.agreement.DatasetId
import nl.knaw.dans.pf.language.emd.EasyMetadata
import org.joda.time.LocalDate

import scala.collection.JavaConverters._

/**
 * Data class for an Easy User. Notice that some fields are mandatory and cannot be null!
 *
 * @param name         the user's name <b>(mandatory!)</b>
 * @param organization the user's organisation
 * @param address      the user's address <b>(mandatory!)</b>
 * @param postalCode   the user's zipcode <b>(mandatory!)</b>
 * @param city         the user's city <b>(mandatory!)</b>
 * @param country      the user's country
 * @param telephone    the user's telephone
 * @param email        the user's email <b>(mandatory!)</b>
 */
case class EasyUser(name: String,
                    organization: String,
                    address: String,
                    postalCode: String,
                    city: String,
                    country: String,
                    telephone: String,
                    email: String,
                   ) {

  require(name != null, "'name' must be defined")
  require(address != null, "'address' must be defined")
  require(postalCode != null, "'postalCode' must be defined")
  require(city != null, "'city' must be defined")
  require(email != null, "'email' must be defined")
}

case class Dataset(datasetId: DatasetId, emd: EasyMetadata, easyUser: EasyUser) {
  require(datasetId != null, "'datasetId' must be defined")

  def doi: String = {
    Option(emd.getEmdIdentifier.getDansManagedDoi).getOrElse("")
  }

  def title: String = emd.getPreferredTitle

  def dateSubmitted: String = {
    emd.getEmdDate
      .getEasDateSubmitted
      .asScala
      .headOption
      .map(_.getValue.toLocalDate)
      .getOrElse(LocalDate.now())
      .toString
  }

  def dateAvailable: String = {
    emd.getEmdDate
      .getEasAvailable
      .asScala
      .headOption
      .map(_.getValue.toLocalDate)
      .getOrElse(LocalDate.now())
      .toString
  }

  def accessCategory: String = accessCategoryEnum.toString

  private def accessCategoryEnum: AccessCategory = {
    Option(emd.getEmdRights.getAccessCategory)
      .getOrElse(OPEN_ACCESS)
  }

  // TODO what to do with old datasets that only have "accept" in the license?
  def license: String = {
    emd.getEmdRights
      .getTermsLicense
      .asScala
      .map(_.getValue)
      .find(_.startsWith("http"))
      .getOrElse(oldLicense)
  }

  def oldLicense: String = {
    accessCategoryEnum match {
      case ANONYMOUS_ACCESS | FREELY_AVAILABLE | OPEN_ACCESS => "http://creativecommons.org/publicdomain/zero/1.0"
      case _ => "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSLicence.pdf"
    }
  }
}
