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
import nl.knaw.dans.easy.agreement.FileAccessRight._
import nl.knaw.dans.easy.agreement.{ DatasetID, FileAccessRight, FileItem }
import nl.knaw.dans.lib.error.TryExtensions
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.string.StringExtensions
import nl.knaw.dans.pf.language.emd.types.Spatial.{ Box, Point }
import nl.knaw.dans.pf.language.emd.types._
import nl.knaw.dans.pf.language.emd.{ EasyMetadata, EmdDate, Term }
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.collection.{ SortedMap, mutable }
import scala.language.{ implicitConversions, postfixOps }
import scala.util.Try

class PlaceholderMapper(metadataTermsFile: File)(implicit parameters: BaseParameters) extends DebugEnhancedLogging {

  type Table = ju.Collection[ju.Map[String, String]]

  val metadataNames: ju.Properties = loadProperties(metadataTermsFile)
    .doIfFailure { case e => logger.error(s"could not read the metadata terms in $metadataTermsFile", e) }
    .getOrElse(new ju.Properties())

  def datasetToPlaceholderMap(dataset: Dataset): Try[PlaceholderMap] = {
    logger.debug("create placeholder map")

    val emd = dataset.emd

    for {
      headerMap <- if (parameters.isSample) sampleHeader(emd)
                   else header(emd)
      dansLogo = DansLogo -> encodeImage(dansLogoFile)
      footer = FooterText -> footerText(footerTextFile)
      depositorMap = depositor(dataset.easyUser)
      accessRightMap <- datasetAccessCategory(emd)
      embargoMap = embargo(emd)
      dateTime = CurrentDateAndTime -> currentDateAndTime
      metadata = MetadataTable -> metadataTable(emd, dataset.audiences, dataset.datasetID)
      files @ (_, table) = FileTable -> filesTable(dataset.fileItems)
      hasFiles = HasFiles -> boolean2Boolean(!table.isEmpty)
      limitFiles = LimitFiles -> parameters.fileLimit.toString
      shouldLimitFiles = ShouldLimitFiles -> boolean2Boolean(dataset.filesLimited)
    } yield headerMap + dansLogo + footer ++ depositorMap ++ accessRightMap ++ embargoMap + dateTime + metadata + files + hasFiles + limitFiles + shouldLimitFiles
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

  def currentDateAndTime: String = new DateTime().toString("YYYY-MM-dd HH:mm:ss")

  private val newLine = "<br/>"

  def metadataTable(emd: EasyMetadata, audiences: Seq[AudienceTitle], datasetID: => DatasetID): Table = {
    emd.getTerms
      .asScala
      .map(term => (term, emd.getTerm(term).asScala))
      .filter { case (_, items) => items.nonEmpty }
      .groupBy { case (term, _) => metadataNames.getProperty(term.getQualifiedName) }
      .map { case (name, termsAndItems) =>
        val (termName, value) = termsAndItems.map {
          case (t, _) if t.getName == Term.Name.AUDIENCE =>
            t.getName -> formatAudience(audiences, datasetID)
          case (t, items) if t.getName == Term.Name.ACCESSRIGHTS =>
            t.getName -> formatDatasetAccessRights(items.head)
          case (t, items) if t.getName == Term.Name.SPATIAL =>
            t.getName -> formatSpatials(items)
          case (t, items) if t.getName == Term.Name.LICENSE =>
            t.getName -> getSpecifiedLicense(items)
              .getOrElse(toLicense(emd.getEmdRights.getAccessCategory))
          case (t, items) if t.getName == Term.Name.RELATION =>
            t.getName -> formatRelations(items)
          case (t, items) => t.getName -> items.mkString(newLine)
        }.reduce[(Term.Name, String)] { case ((t1, s1), (t2, s2)) if t1 == t2 => (t1, s1 + newLine * 2 + s2) }

        // keep the Term.Name around for sorting according to the Enum order
        termName -> Map(
          MetadataKey -> name,
          MetadataValue -> value
        ).keywordMapAsJava
      }
      .sortedJavaCollection
  }

  private def getSpecifiedLicense(licenseItems: mutable.Buffer[MetadataItem]) = {
    licenseItems.filterNot {
      case s: BasicString if s.getValue == "accept" => true
      case _ => false
    }.map(_.toString).headOption
  }

  private def toLicense(category: AccessCategory) = {
    category match {
      case AccessCategory.OPEN_ACCESS => "http://creativecommons.org/publicdomain/zero/1.0/legalcode"
      case _ => "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSGeneralconditionsofuseUKDEF.pdf"
    }
  }

  private def formatRelations(items: mutable.Buffer[MetadataItem]) = {
    items.map {
      case r: Relation => formatRelation(r)
      case s => s.toString
    }.mkString(newLine)
  }

  def formatAudience(audiences: Seq[AudienceTitle], datasetID: => DatasetID): String = {
    // may throw an UnsupportedOperationException
    Try(audiences.reduce(_ + "; " + _))
      .doIfFailure {
        case _: UnsupportedOperationException => logger.warn(s"Found a dataset with no audience: $datasetID. Returning an empty String instead.")
      }
      .getOrElse("")
  }

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
      .doIfFailure { case _ => logger.warn("No available mapping; using acces category value directly") }
      .getOrElse(item.toString)
  }

  private def formatSpatials(items: mutable.Buffer[MetadataItem]) = {
    val basic = items.collect {
      case s: BasicString => s.getValue
    }
    val spatial = items.collect {
      case s: Spatial => formatEasSpatial(s).replace("\n", newLine)
    }
    (basic ++ spatial).mkString(newLine * 2)
  }

  def formatEasSpatial(spatial: Spatial): String = {
    val place = Option(spatial.getPlace).flatMap(_.getValue.toOption)
    val point = Option(spatial.getPoint).map(formatPoint)
    val box = Option(spatial.getBox).map(formatBox)
    val polygonText = Option(spatial.getPolygons).flatMap(_.asScala.headOption).map(formatPolygon)

    (place.toList ::: point.toList ::: box.toList ::: polygonText.toList).mkString("\n")
  }

  private def formatPoint(point: Point): String = {
    val scheme = Option(point.getScheme).map("scheme = " + _ + ", ").getOrElse("")
    val x = s"x = ${ point.getX }"
    val y = s"y = ${ point.getY }"

    // note: no space between $scheme and $x:
    // if $scheme is defined, it will do the space after it by itself;
    // if $scheme is not defined, it doesn't require the extra space.
    s"<b>Point</b>: $scheme$x, $y"
  }

  private def formatBox(box: Box): String = {
    val scheme = Option(box.getScheme).map("scheme = " + _ + ", ").getOrElse("")
    val north = s"north = ${ box.getNorth }"
    val east = s"east = ${ box.getEast }"
    val south = s"south = ${ box.getSouth }"
    val west = s"west = ${ box.getWest }"

    s"<b>Box:</b> $scheme$north, $east, $south, $west"
  }

  private def formatPolygon(polygon: Polygon): String = {
    s"""<b>Polygon:</b>
       |<i>To keep this agreement at a reasonable size the polygon coordinates are omitted. For a full listing of the polygons please contact DANS at <a href="mailto:info@dans.knaw.nl">info@dans.knaw.nl</a>.</i>""".stripMargin
  }

  private def formatRelation(relation: Relation): String = {
    val title = Option(relation.getSubjectTitle).flatMap(_.getValue.toOption.map("title = " +))
    val url = Option(relation.getSubjectLink).map("url = " +)

    title.map(t => url.fold(t)(u => s"$t, $u"))
      .orElse(url)
      .getOrElse("")
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

  def filesTable(fileItems: Seq[FileItem]): Table = {
    fileItems
      .map { case FileItem(path, accessibleTo, checkSum) =>
        val map = Map(
          FilePath -> path,
          FileChecksum -> checkSum.filterNot(_.isBlank).filterNot("none" ==).getOrElse(checkSumNotCalculated),
          FileAccessibleTo -> formatFileAccessRights(accessibleTo)
        )

        (path, map.keywordMapAsJava)
      }
      .sortedJavaCollection
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
