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

import java.io.{ File, FileOutputStream }

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.internal.{ BaseParameters, Dataset, EasyUser, PlaceholderMapper, VelocityTemplateResolver, velocityProperties }
import nl.knaw.dans.lib.error._
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.IsoDate
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.prop.TableDrivenPropertyChecks

import scala.util.{ Success, Try }

class TemplateSpec extends UnitSpec with MockFactory with TableDrivenPropertyChecks {
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
    forEvery(for {
      rights <- Seq(AccessCategory.OPEN_ACCESS, AccessCategory.NO_ACCESS)
      isSample <- Seq(true, false)
      available <- Seq(new DateTime, (new DateTime).plusYears(1)).map(new IsoDate(_))
    } yield (isSample, rights, available)) {
      case (isSample, rights, available) =>
        createDoc(isSample, rights, available) shouldBe Success(())
    }
  }

  private def createDoc(isSample: Boolean, rights: AccessCategory, available: IsoDate): Try[Unit] = {
    val dataset = mockDataset(rights, isSample, available)
    val outputStream = new FileOutputStream(docName(isSample, rights, available))
    implicit val parameters: BaseParameters = new BaseParameters(templateResourceDir, datasetId, isSample)
    val placeholders = new PlaceholderMapper(properties)
      .datasetToPlaceholderMap(dataset)
      .getOrRecover(fail(_))
    new VelocityTemplateResolver(velocityProperties)
      .createTemplate(outputStream, placeholders)
  }

  private def docName(isSample: Boolean, rights: AccessCategory, available: IsoDate): String = {
    s"$testDir/$rights-${
      if (isSample) "sample-"
      else ""
    }$available.html"
  }

  private def mockDataset(openaccess: AccessCategory, isSample: Boolean, dateAvailable: IsoDate): Dataset = {
    val emd = mock[MockEasyMetadata]
    val date = mock[EmdDate]
    val rights = mock[EmdRights]
    emd.getPreferredTitle _ expects() returning "about testing"
    emd.getEmdRights _ expects() returning rights
    emd.getEmdDate _ expects() returning date twice()
    rights.getAccessCategory _ expects() returning openaccess
    date.getEasDateSubmitted _ expects() returning java.util.Arrays.asList(new IsoDate("1992-07-30"))
    date.getEasAvailable _ expects() returning java.util.Arrays.asList(dateAvailable)
    if (!isSample) {
      val emdIdentifier = mock[EmdIdentifier]
      emd.getEmdIdentifier _ expects() returning emdIdentifier anyNumberOfTimes()
      emdIdentifier.getDansManagedDoi _ expects() returning "10.17026/test-dans-2xg-umq8" anyNumberOfTimes()
    }
    Dataset(datasetID = "easy:12", emd, user).validate
  }
}
