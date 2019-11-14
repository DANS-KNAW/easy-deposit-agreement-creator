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

import java.io._
import java.net.URLEncoder
import java.{ util => ju }

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.common.lang.dataset.AccessCategory._
import nl.knaw.dans.easy.agreement.FileAccessRight
import nl.knaw.dans.easy.agreement.FileAccessRight._
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.pf.language.emd.types._
import nl.knaw.dans.pf.language.emd.{ EasyMetadata, EmdDate }
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.collection.SortedMap
import scala.language.{ implicitConversions, postfixOps }
import scala.util.Try

class PlaceholderMapper(metadataTermsFile: File)(implicit parameters: BaseParameters) extends DebugEnhancedLogging {

  type Table = ju.Collection[ju.Map[String, String]]

  def datasetToPlaceholderMap(dataset: Dataset): Try[PlaceholderMap] = {
    logger.debug("create placeholder map")

    val emd = dataset.emd

    for {
      headerMap <- if (parameters.isSample) sampleHeader(emd)
                   else header(emd)
      dansLogo = DansLogo -> encodeImage(dansLogoFile)
      drivenByData = DrivenByData -> encodeImage(drivenByDataFile)
      footer = FooterText -> footerText(footerTextFile)
      depositorMap = depositor(dataset.easyUser)
      accessRightMap <- datasetAccessCategory(emd)
      termsLicenseMap <- termsLicenseMap(emd)
      embargoMap = embargo(emd)
      dateTime = CurrentDateAndTime -> currentDateAndTime
    } yield headerMap + dansLogo + drivenByData + footer ++ depositorMap ++ accessRightMap ++ termsLicenseMap ++ embargoMap + dateTime
  }

  def header(emd: EasyMetadata): Try[PlaceholderMap] = Try {
    val doi = Option(emd.getEmdIdentifier.getDansManagedDoi)

    Map(
      IsSample -> boolean2Boolean(false),
      DansManagedDoi -> doi.getOrElse(""),
      // the following can throw an UnsupportedEncodingException, although this is not expected to ever happen!
      DansManagedEncodedDoi -> doi.map(URLEncoder.encode(_, encoding.displayName())).getOrElse(""),
      DateSubmitted -> getDate(emd)(_.getEasDateSubmitted).getOrElse(new IsoDate()).toString,
      Title -> emd.getPreferredTitle
    )
  }

  def sampleHeader(emd: EasyMetadata): Try[PlaceholderMap] = Try {
    Map(
      IsSample -> boolean2Boolean(true),
      DateSubmitted -> getDate(emd)(_.getEasDateSubmitted).getOrElse(new IsoDate()).toString,
      Title -> emd.getPreferredTitle
    )
  }

  def encodeImage(file: File): String = {
    new String(Base64.encodeBase64(FileUtils.readFileToByteArray(file)))
  }

  def footerText(file: File): String = file.read().stripLineEnd

  def getDate(emd: EasyMetadata)(f: EmdDate => ju.List[IsoDate]): Option[IsoDate] = {
    f(emd.getEmdDate).asScala.headOption
  }

  def depositor(depositor: EasyUser): PlaceholderMap = {
    Map(
      DepositorName -> depositor.name,
      DepositorOrganisation -> depositor.organization,
      DepositorAddress -> depositor.address,
      DepositorPostalCode -> depositor.postalCode,
      DepositorCity -> depositor.city,
      DepositorCountry -> depositor.country,
      DepositorTelephone -> depositor.telephone,
      DepositorEmail -> depositor.email
    )
  }

  def datasetAccessCategory(emd: EasyMetadata): Try[PlaceholderMap] = Try {
    // access category in EMD may be null, in which case OPEN_ACCESS is the default value
    val ac = Option(emd.getEmdRights.getAccessCategory).getOrElse(OPEN_ACCESS)

    val result = Map[KeywordMapping, List[AccessCategory]](
      OpenAccess -> List(OPEN_ACCESS, ANONYMOUS_ACCESS, FREELY_AVAILABLE),
      OpenAccessForRegisteredUsers -> List(OPEN_ACCESS_FOR_REGISTERED_USERS),
      OtherAccess -> List(ACCESS_ELSEWHERE, NO_ACCESS),
      RestrictGroup -> List(GROUP_ACCESS),
      RestrictRequest -> List(REQUEST_PERMISSION)
      // because Velocity requires Java objects, we transform Scala's Boolean into a Java Boolean
    ).mapValues(lst => boolean2Boolean(lst.contains(ac)))

    if (result.exists { case (_, bool) => bool == true })
      result
    else
      throw new IllegalArgumentException(s"The specified access category ($ac) does not map to any of these keywords.")
  }

  def embargo(emd: EasyMetadata): PlaceholderMap = {
    val dateAvailable = getDate(emd)(_.getEasAvailable).map(_.getValue)
    Map(
      // because Velocity requires Java objects, we transform Scala's Boolean into a Java Boolean
      UnderEmbargo -> boolean2Boolean(dateAvailable.exists(new DateTime().plusMinutes(1).isBefore)),
      DateAvailable -> dateAvailable.map(_.toString("YYYY-MM-dd")).getOrElse("")
    )
  }

  def termsLicenseMap(emd: EasyMetadata): Try[PlaceholderMap] = Try {
    val url = emd.getEmdRights.getTermsLicense.asScala
      .map(_.getValue)
      .find(_.startsWith("http"))
      .getOrElse(throw new IllegalArgumentException("Did not find a <emd:rights><dct:license>http..."))
    val file = parameters.licenseLegalResource(url)
    Map(
      TermsLicenseUrl -> url,
      TermsLicense -> new File(file).getName.replaceAll(".[^.]+$","").replaceAll(".[^.]/",""),
      Appendix3 -> file.toString
    )
  }

  def currentDateAndTime: String = new DateTime().toString("YYYY-MM-dd HH:mm:ss")

  def formatDatasetAccessRights(item: MetadataItem): String = {
    Try(AccessCategory.valueOf(item.toString)) // may throw an IllegalArgumentException
      .map {
        // @formatter:off
        case ANONYMOUS_ACCESS                 => "Anonymous"
        case OPEN_ACCESS                      => "Open Access"
        case OPEN_ACCESS_FOR_REGISTERED_USERS => "Open access for registered users"
        case GROUP_ACCESS                     => "Restricted - 'archaeology' group"
        case REQUEST_PERMISSION               => "Restricted - request permission"
        case ACCESS_ELSEWHERE                 => "Elsewhere"
        case NO_ACCESS                        => "Other"
        case FREELY_AVAILABLE                 => "Open Access"
        // @formatter:on
      }
      .doIfFailure { case _ => logger.warn("No available mapping; using access category value directly") }
      .getOrElse(item.toString)
  }

  def formatFileAccessRights(accessRight: FileAccessRight.Value): String = {
    accessRight match {
      // @formatter:off
      case ANONYMOUS          => "Anonymous"
      case KNOWN              => "Known"
      case RESTRICTED_REQUEST => "Restricted request"
      case RESTRICTED_GROUP   => "Restricted group"
      case NONE               => "None"
      // @formatter:on
    }
  }

  implicit class KeywordMapToJavaMap[Keyword <: KeywordMapping](map: Map[Keyword, String]) {
    def keywordMapAsJava: ju.Map[String, String] = map.map { case (k, v) => (k.keyword, v) }.asJava
  }

  implicit class SortedJavaCollection[T: Ordering, S](collection: Iterable[(T, S)]) {
    def sortedJavaCollection: ju.Collection[S] = {
      collection.foldLeft(SortedMap[T, S]())(_ + _)
        .values
        .asJavaCollection
    }
  }
}
