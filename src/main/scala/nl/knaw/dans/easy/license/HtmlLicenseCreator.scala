package nl.knaw.dans.easy.license

import java.io.{File, FileInputStream, FileWriter}
import java.net.URLEncoder
import java.nio.charset.Charset
import java.{util => ju}

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.common.lang.dataset.AccessCategory._
import nl.knaw.dans.pf.language.emd.types.IsoDate
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EmdDate}
import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.joda.time.DateTime

import scala.collection.JavaConverters._
import scala.language.implicitConversions
import scala.util.Try

object HtmlLicenseCreator {

  def datasetToPlaceholderMap(dataset: Dataset): PlaceholderMap = {
    val emd = dataset.emd

    header(emd) ++
      users(dataset.easyUser) ++
      accessRights(emd) ++
      embargo(emd) +
      currentDateAndTime
  }

  def header(emd: EasyMetadata): PlaceholderMap = {
    Map(
      DansManagedDoi -> getDansManagedDoi(emd).getOrElse(""),
      DansManagedEncodedDoi -> getDansManagedEncodedDoi(emd).getOrElse(""),
      DateSubmitted -> getDate(emd)(_.getEasDateSubmitted).getOrElse(new IsoDate()).toString,
      Title -> emd.getPreferredTitle
    )
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

  def users(user: EasyUser): PlaceholderMap = {
    Map(
      UserName -> user.name,
      UserOrganisation -> user.organization,
      UserAddress -> user.address,
      UserPostalCode -> user.postalCode,
      UserCity -> user.city,
      UserCountry -> user.country,
      UserTelephone -> user.telephone,
      UserEmail -> user.email
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

  def currentDateAndTime = CurrentDateAndTime -> new DateTime().toString("YYYY-MM-dd HH:mm:ss")
}

class VelocityTemplateResolver(propertiesFile: File)(implicit parameters: Parameters) {

  val properties = {
    val properties = new ju.Properties
    properties.load(new FileInputStream(propertiesFile))
    properties
  }
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
    * Create the template on location `templateFile` after filling in the placeholders with `map`.
    * If an `Exception` occurs, `templateFile` is not created/deleted.
    *
    * @param templateFile The location where to store the template
    * @param map The mapping between placeholders and actual values
    * @param encoding the encoding to be used in writing to `templateFile`
    * @return `Success` if creating a template succeeded, `Failure` otherwise
    */
  def createTemplate(templateFile: File, map: PlaceholderMap, encoding: Charset = encoding) = {
    Try {
      val context = new VelocityContext(map.map { case (kw, o) => (kw.keyword, o) }.asJava)
      val writer = new FileWriter(templateFile)

      engine.getTemplate(templateFileName, encoding.displayName()).merge(context, writer)
      writer.flush()
      writer.close()
    }.doOnError(_ => templateFile.delete())
  }
}
