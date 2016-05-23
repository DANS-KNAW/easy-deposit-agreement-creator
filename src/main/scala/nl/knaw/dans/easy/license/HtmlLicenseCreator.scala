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

import java.io._
import java.net.URLEncoder
import java.nio.charset.Charset
import java.{util => ju}

import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.common.lang.dataset.AccessCategory._
import nl.knaw.dans.pf.language.emd.types.{IsoDate, MetadataItem}
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EmdDate, Term}
import org.apache.commons.codec.binary.Base64
import org.apache.commons.io.FileUtils
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.joda.time.DateTime
import org.slf4j.LoggerFactory
import rx.lang.scala.{Observable, ObservableExtensions}

import scala.collection.JavaConverters._
import scala.language.{implicitConversions, postfixOps}
import scala.util.Try

class HtmlLicenseCreator(metadataTermsFile: File)(implicit parameters: Parameters) {

  val log = LoggerFactory.getLogger(getClass)

  val metadataNames = loadProperties(metadataTermsFile)
    .doOnError(e => log.error(s"could not read the metadata terms in $metadataTermsFile", e))
    .getOrElse(new ju.Properties())

  def datasetToPlaceholderMap(dataset: Dataset)(implicit client: FedoraClient): Observable[PlaceholderMap] = {
    val emd = dataset.emd

    val placeholders = header(emd) +
      (DansLogo -> encodeImage(dansLogoFile)) ++
      depositor(dataset.easyUser) ++
      accessRights(emd) ++
      embargo(emd) +
      (CurrentDateAndTime -> currentDateAndTime)

    val metadata = metadataTable(emd).map(MetadataTable -> _)
    val files = filesTable(dataset.datasetID).map(FileTable -> _)
    metadata.zipWith(files)((meta, file) =>
      (map: Map[KeywordMapping, Object]) => map + meta + file + (HasFiles -> boolean2Boolean(!file._2.isEmpty)))
      .map(_(placeholders))
  }

  def header(emd: EasyMetadata): PlaceholderMap = {
    Map(
      DansManagedDoi -> getDansManagedDoi(emd).getOrElse(""),
      DansManagedEncodedDoi -> getDansManagedEncodedDoi(emd).getOrElse(""),
      DateSubmitted -> getDate(emd)(_.getEasDateSubmitted).getOrElse(new IsoDate()).toString,
      Title -> emd.getPreferredTitle
    )
  }

  private def encodeImage(file: File): String = {
    new String(Base64.encodeBase64(FileUtils.readFileToByteArray(file)))
  }

  private def getDansManagedDoi(emd: EasyMetadata): Option[String] = {
    Option(emd.getEmdIdentifier.getDansManagedDoi)
  }

  // this may throw an UnsupportedEncodingException, although this is not expected!
  private def getDansManagedEncodedDoi(emd: EasyMetadata): Option[String] = {
    getDansManagedDoi(emd).map(doi => URLEncoder.encode(doi, encoding.displayName()))
  }

  private def getDate(emd: EasyMetadata)(f: EmdDate => ju.List[IsoDate]) = {
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

  def accessRights(emd: EasyMetadata): PlaceholderMap = {
    val ac = Option(emd.getEmdRights.getAccessCategory).getOrElse(OPEN_ACCESS)

    Map[KeywordMapping, List[AccessCategory]](
      OpenAccess -> List(OPEN_ACCESS, ANONYMOUS_ACCESS, FREELY_AVAILABLE),
      OpenAccessForRegisteredUsers -> List(OPEN_ACCESS_FOR_REGISTERED_USERS),
      OtherAccess -> List(GROUP_ACCESS),
      RestrictGroup -> List(REQUEST_PERMISSION),
      RestrictRequest -> List(ACCESS_ELSEWHERE, NO_ACCESS)
    ).mapValues(lst => boolean2Boolean(lst.contains(ac)))
  }

  def embargo(emd: EasyMetadata): PlaceholderMap = {
    val dateAvailable = getDate(emd)(_.getEasAvailable).map(_.getValue)
    Map(
      UnderEmbargo -> boolean2Boolean(dateAvailable.exists(new DateTime().plusMinutes(1).isBefore)),
      DateAvailable -> dateAvailable.map(_.toString("YYYY-MM-dd")).getOrElse("")
    )
  }

  def currentDateAndTime = new DateTime().toString("YYYY-MM-dd HH:mm:ss")

  def metadataTable(emd: EasyMetadata)(implicit client: FedoraClient): Observable[ju.List[ju.Map[String, String]]] = {
    emd.getTerms
      .asScala
      .toObservable
      .map(term => (term, emd.getTerm(term).asScala))
      .filter { case (_, items) => items.nonEmpty }
      .flatMap { case (term, items) =>
        val name = metadataNames.getProperty(term.getQualifiedName)
        val valueObs = if (term.getName == Term.Name.AUDIENCE) formatAudience(emd)
        else if (term.getName == Term.Name.ACCESSRIGHTS) Observable.just(formatAccessRights(items.head))
        else Observable.just(items.mkString(", "))

        valueObs.map(value => Map[KeywordMapping, String](MetadataKey -> name, MetadataValue -> value)
          .map { case (k, v) => (k.keyword, v) }
          .asJava)
      }
      .foldLeft(new ju.ArrayList[ju.Map[String, String]])((list, map) => { list.add(map); list })
  }

  private def formatAudience(emd: EasyMetadata)(implicit client: FedoraClient): Observable[String] = {
    // TODO I need a DisciplineCollectionService or something???

    emd.getEmdAudience.getValues.asScala.toObservable
      .flatMap(sid => queryFedora(sid, "DC")(_.loadXML \\ "title" text))
      .reduce(_ + "; " + _)
  }

  private def formatAccessRights(item: MetadataItem): String = {
    Try(AccessCategory.valueOf(item.toString)) // may throw an IllegalArgumentException
      .map {
        // @formatter:off
        case ANONYMOUS_ACCESS                 => "Anonymous"
        case OPEN_ACCESS                      => "Open Access"
        case OPEN_ACCESS_FOR_REGISTERED_USERS => "Open access for registered users"
        case GROUP_ACCESS                     => "Restricted -'archaeology' group"
        case REQUEST_PERMISSION               => "Restricted -request permission"
        case ACCESS_ELSEWHERE                 => "Elsewhere"
        case NO_ACCESS                        => "Other"
        case FREELY_AVAILABLE                 => "Open Access"
        // @formatter:on
      }.doOnError(e => log.warn("No available mapping; using acces category value directly"))
        .getOrElse(item.toString)
  }

  def filesTable(datasetID: DatasetID)(implicit client: FedoraClient): Observable[ju.List[ju.Map[String, String]]] = {
    lazy val query = "PREFIX dans: <http://dans.knaw.nl/ontologies/relations#> " +
      "PREFIX fmodel: <info:fedora/fedora-system:def/model#> " +
      s"SELECT ?s WHERE {?s dans:isSubordinateTo <info:fedora/$datasetID> . " +
      "?s fmodel:hasModel <info:fedora/easy-model:EDM1FILE>}"

    val xs = for {
      filePid <- queryRiSearch(query)
      path <- queryFedora(filePid, "EASY_FILE_METADATA")(_.loadXML \\ "path" text)
      cs = FedoraClient.getDatastream(filePid, "EASY_FILE").execute(client).getDatastreamProfile.getDsChecksum
      // check for both blank String and 'none' because checksum may be turned off or there is no checksum calculated
      checksum = if (cs.isBlank || cs == "none") "-------------not-calculated-------------" else cs
      map = Map[KeywordMapping, String](FileKey -> path, FileValue -> checksum).map { case (k, v) => (k.keyword, v) }.asJava
    } yield map

    xs.foldLeft(new ju.ArrayList[ju.Map[String, String]])((list, map) => { list.add(map); list })
  }
}

class VelocityTemplateResolver(propertiesFile: File)(implicit parameters: Parameters) {

  val log = LoggerFactory.getLogger(getClass)

  val properties = loadProperties(propertiesFile)
    .doOnError(e => log.error(s"could not read the velocity properties in $propertiesFile", e))
    .getOrElse(new ju.Properties())

  val velocityResources = new File(properties.getProperty("file.resource.loader.path"))
  val templateFileName = properties.getProperty("template.file.name")

  val doc = new File(velocityResources, templateFileName)
  assert(doc.exists(), s"file does not exist - $doc")

  val engine = {
    val engine = new VelocityEngine(properties)
    engine.init()
    engine
  }

  /**
    * Create the template and write it to `out` after filling in the placeholders with `map`.
    *
    * @param out The `OutputStream` where the filled in template is written to
    * @param map The mapping between placeholders and actual values
    * @param encoding The encoding to be wused in writing to `out`
    * @return `Success` if filling in the template succeeded, `Failure` otherwise
    */
  def createTemplate(out: OutputStream, map: PlaceholderMap, encoding: Charset = encoding) = {
    new OutputStreamWriter(out).use(writer => {
      val context = new VelocityContext
      map.foreach { case (kw, o) => context.put(kw.keyword, o) }

      engine.getTemplate(templateFileName, encoding.displayName()).merge(context, writer)
    })
  }
}
