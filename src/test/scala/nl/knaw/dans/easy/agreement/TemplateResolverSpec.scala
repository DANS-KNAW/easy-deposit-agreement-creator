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
package nl.knaw.dans.easy.agreement

import java.io.{ ByteArrayOutputStream, File, FileOutputStream, OutputStream }
import java.util.Arrays.asList

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.internal._
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.{ BasicString, IsoDate }
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.{ Failure, Success }

class TemplateResolverSpec extends UnitSpec with MockFactory with TableDrivenPropertyChecks with BeforeAndAfterAll {
  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""

    def toString(x: String, y: Term): String = ""

    def toString(x: String, y: MDContainer): String = ""

    def toString(x: String): String = ""
  }

  private val templateResourceDir = new java.io.File(testDir, "res")
  private val properties = new File(templateResourceDir, "MetadataTestTerms.properties")
  private val datasetId = "easy:12"
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

  override def beforeAll() {
    testDir.mkdirs()
    super.beforeAll()
    testDir.deleteDirectory()
    testDir.mkdirs()
    new File("src/main/assembly/dist/res").copyDir(new File(testDir, "res"))
    new File("target/easy-licenses/licenses").copyDir(new File(testDir, "res/template/licenses"))
  }

  "createTemplate" should "find all place holders" in {
    forEvery(for {
      rights <- Seq(AccessCategory.OPEN_ACCESS, AccessCategory.NO_ACCESS)
      isSample <- Seq(true, false)
      available <- Seq(new DateTime, (new DateTime).plusYears(1)).map(new IsoDate(_))
    } yield (isSample, rights, available)) {
      case (isSample, rights, available) =>
        create(
          isSample,
          mockDataset(rights, isSample, available),
          new FileOutputStream(docName(isSample, rights, available))
        ) shouldBe Success(())
    }
  }

  it should "not find a license url" in {
    val dataset = mockDataset(AccessCategory.OPEN_ACCESS, isSample = false, new IsoDate(), new BasicString("accept"))
    val outputStream = new ByteArrayOutputStream()

    create(isSample = false, dataset, outputStream) should matchPattern {
      case Failure(e: IllegalArgumentException) if (e.getMessage == "Did not find a <emd:rights><dct:license>http...") =>
    }
  }

  it should "not find the resource for Appendix 3" in {
    val dataset = mockDataset(AccessCategory.OPEN_ACCESS, isSample = false, new IsoDate(), new BasicString("http://dans.knaw.nl"))
    val outputStream = new ByteArrayOutputStream()

    create(isSample = false, dataset, outputStream) should matchPattern {
      case Failure(e: java.lang.IllegalArgumentException) if (e.getMessage == "No legal text found for http://dans.knaw.nl") =>
    }
  }

  it should "properly format all types of licenses" in {
    forEvery(Seq(
      new BasicString("http://creativecommons.org/licenses/by-nc-sa/3.0")
        -> """BY-NC-SA-3.0 : <a href="http://creativecommons.org/licenses/by-nc-sa/3.0">http://creativecommons.org/licenses/by-nc-sa/3.0</a>""",
      new BasicString("https://creativecommons.org/licenses/by-nc-sa/3.0")
        -> """BY-NC-SA-3.0 : <a href="https://creativecommons.org/licenses/by-nc-sa/3.0">https://creativecommons.org/licenses/by-nc-sa/3.0</a>""",
      new BasicString("https://www.creativecommons.org/licenses/by-nc-sa/3.0")
        -> """BY-NC-SA-3.0 : <a href="https://www.creativecommons.org/licenses/by-nc-sa/3.0">https://www.creativecommons.org/licenses/by-nc-sa/3.0</a>""",
      new BasicString("http://www.creativecommons.org/licenses/by-nc-sa/3.0")
        -> """BY-NC-SA-3.0 : <a href="http://www.creativecommons.org/licenses/by-nc-sa/3.0">http://www.creativecommons.org/licenses/by-nc-sa/3.0</a>""",
    )) {
      case (emdRightsTermsLicense, expected) =>
        val dataset = mockDataset(AccessCategory.OPEN_ACCESS, isSample = false, new IsoDate(), emdRightsTermsLicense)
        val outputStream = new ByteArrayOutputStream()

        create(isSample = false, dataset, outputStream) shouldBe Success(())

        val lines = outputStream.toString.split("\n")
        //   .filter(_.contains("""class="choice">""")) // TODO less output in case of failure, but it's no longer on a single line
        //  lines should have length (1) // detected a forgotten #else in the template
        lines.mkString("\n", "\n", "\n") should include(expected)
    }
  }

  private def create(isSample: Boolean, dataset: Dataset, outputStream: OutputStream) = {
    implicit val parameters: BaseParameters = new BaseParameters(templateResourceDir, datasetId, isSample)
    new PlaceholderMapper(properties)
      .datasetToPlaceholderMap(dataset)
      .flatMap(placeholders =>
        new VelocityTemplateResolver(velocityProperties)
          .createTemplate(outputStream, placeholders)
      )
  }

  private def docName(isSample: Boolean, rights: AccessCategory, available: IsoDate): String = {
    s"$testDir/$rights-${
      if (isSample) "sample-"
      else ""
    }$available.html"
  }

  private def mockDataset(openaccess: AccessCategory, isSample: Boolean, dateAvailable: IsoDate,
                          emdRightsTermsLicense: BasicString = new BasicString("http://creativecommons.org/licenses/by-nc-sa/3.0")
                         ): Dataset = {
    val emd = mock[MockEasyMetadata]
    val date = mock[EmdDate]
    val rights = mock[EmdRights]
    emd.getPreferredTitle _ expects() returning "about testing"
    emd.getEmdRights _ expects() returning rights twice()
    emd.getEmdDate _ expects() returning date anyNumberOfTimes()
    rights.getAccessCategory _ expects() returning openaccess
    rights.getTermsLicense _ expects() returning asList(emdRightsTermsLicense)
    date.getEasDateSubmitted _ expects() returning asList(new IsoDate("1992-07-30"))
    date.getEasAvailable _ expects() returning asList(dateAvailable) anyNumberOfTimes()
    if (!isSample) {
      val emdIdentifier = mock[EmdIdentifier]
      emd.getEmdIdentifier _ expects() returning emdIdentifier anyNumberOfTimes()
      emdIdentifier.getDansManagedDoi _ expects() returning "10.17026/test-dans-2xg-umq8" anyNumberOfTimes()
    }
    Dataset(datasetID = "easy:12", emd, user).validate
  }
}
