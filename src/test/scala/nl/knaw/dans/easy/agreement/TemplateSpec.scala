package nl.knaw.dans.easy.agreement

import java.io.{ File, FileOutputStream }

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.internal.{ BaseParameters, Dataset, EasyUser, PlaceholderMapper, VelocityTemplateResolver, velocityProperties }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.IsoDate
import org.scalamock.scalatest.MockFactory

import scala.util.Success

class TemplateSpec extends UnitSpec with MockFactory {
  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""

    def toString(x: String, y: Term): String = ""

    def toString(x: String, y: MDContainer): String = ""

    def toString(x: String): String = ""
  }

  private val templateResourceDir = new java.io.File("src/main/assembly/dist/res")
  val properties = new File(templateResourceDir, "MetadataTestTerms.properties")
  val datasetId = "easy:12"
  private val user = EasyUser(
    name = "N.O. Body",
    organization = "Eidgenössische Technische Hochschule",
    address = "Rämistrasse 101",
    postalCode = "8092",
    city = " Zürich",
    country = "Schweiz",
    telephone = "+41 44 632 11 11",
    email = "nobody@dans.knaw.nl"
  )
  testDir.mkdirs()

  "createTemplate" should "find all place holders" in {
    val isSample = true
    implicit val parameters: BaseParameters = new BaseParameters(templateResourceDir, datasetId, isSample, fileLimit = 3)
    val placeholders = new PlaceholderMapper(properties)
      .datasetToPlaceholderMap(getDataset(AccessCategory.OPEN_ACCESS, isSample))
      .getOrRecover(fail(_))
    new VelocityTemplateResolver(velocityProperties)
      .createTemplate(new FileOutputStream(testDir + "/openAccess.html"), placeholders) shouldBe Success(())
  }

  "createTemplate" should "handle a sample" in {
    val isSample = false
    implicit val parameters: BaseParameters = new BaseParameters(templateResourceDir, datasetId, isSample, fileLimit = 3)
    val placeholders = new PlaceholderMapper(properties)
      .datasetToPlaceholderMap(getDataset(AccessCategory.NO_ACCESS, isSample))
      .getOrRecover(fail(_))
    new VelocityTemplateResolver(velocityProperties)
      .createTemplate(new FileOutputStream(testDir + "/noAccessSample.html"), placeholders) shouldBe Success(())
  }

  private def getDataset(openaccess: AccessCategory, isSample: Boolean) = {
    val emd = mock[MockEasyMetadata]
    val date = mock[EmdDate]
    val rights = mock[EmdRights]
    emd.getPreferredTitle _ expects() returning "about testing"
    emd.getEmdRights _ expects() returning rights
    emd.getEmdDate _ expects() returning date twice()
    rights.getAccessCategory _ expects() returning openaccess
    date.getEasDateSubmitted _ expects() returning java.util.Arrays.asList(new IsoDate("1992-07-30"))
    date.getEasAvailable _ expects() returning java.util.Arrays.asList(new IsoDate("1992-07-30"))
    if (!isSample) {
      val emdIdentifier = mock[EmdIdentifier]
      emd.getEmdIdentifier _ expects() returning emdIdentifier anyNumberOfTimes()
      emdIdentifier.getDansManagedDoi _ expects() returning "10.17026/test-dans-2xg-umq8" anyNumberOfTimes()
    }
    Dataset(datasetID = "easy:12", emd, user, audiences = null, fileItems = Seq.empty, filesLimited = true).validate
  }
}
