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
package nl.knaw.dans.easy.agreement.datafetch

import better.files.File
import javax.naming.directory.BasicAttributes
import nl.knaw.dans.easy.agreement.DepositorId
import nl.knaw.dans.easy.agreement.fixture.{ FileSystemSupport, TestSupportFixture }
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfterAll, BeforeAndAfterEach }

import scala.collection.JavaConverters._
import scala.language.reflectiveCalls
import scala.util.{ Success, Try }

class DatasetLoaderSpec extends TestSupportFixture
  with FileSystemSupport
  with MockFactory
  with BeforeAndAfterEach
  with BeforeAndAfterAll {

  private val fedoraMock = mock[Fedora]
  private val ldapMock = mock[Ldap]

  val (userAttributes, expectedUser) = {
    val attrs = new BasicAttributes
    attrs.put("displayname", "name")
    attrs.put("o", "org")
    attrs.put("postaladdress", "addr")
    attrs.put("postalcode", "pc")
    attrs.put("l", "city")
    attrs.put("st", "cntr")
    attrs.put("telephonenumber", "phone")
    attrs.put("mail", "mail")

    val user = EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")

    (attrs, user)
  }

  override def beforeEach(): Unit = {
    super.beforeEach()
    File(getClass.getResource("/datasetloader")).copyTo(testDir / "datasetloader")
  }

  "getUserById" should "query the user data from ldap for a given user id" in {
    ldapMock.query _ expects "testID" returning Success(userAttributes)

    val loader = new DatasetLoaderImpl(fedoraMock, ldapMock)
    loader.getUserById("testID") should matchPattern { case Success(`expectedUser`) => }
  }

  it should "default to an empty String if the field is not available in the attributes" in {
    ldapMock.query _ expects "testID" returning Success(new BasicAttributes)

    val loader = new DatasetLoaderImpl(fedoraMock, ldapMock)
    loader.getUserById("testID") should matchPattern { case Success(EasyUser("", "", "", "", "", "", "", "")) => }
  }

  "getDatasetById" should "return the dataset corresponding to the given identifier" in {
    val id = "testID"
    val depID = "depID"
    val user = EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")

    val amdStream = IOUtils.toInputStream(<foo><depositorId>{depID}</depositorId></foo>.toString)
    val emdStream = FileUtils.openInputStream((testDir / "datasetloader" / "emd.xml").toJava)

    fedoraMock.getAMD _ expects id returning Success(amdStream)
    fedoraMock.getEMD _ expects id returning Success(emdStream)

    val loader = new DatasetLoaderImpl(fedoraMock, ldapMock) {
      override def getUserById(depositorId: DepositorId): Try[EasyUser] = {
        if (depositorId == depID) Success(user)
        else fail(s"not the correct depositorID, was $depositorId, should be $depID")
      }
    }
    loader.getDatasetById(id)
      // there is no equals defined for the emd, so I need to unpack here
      .map { case Dataset(datasetID, emd, usr) =>
        (datasetID, emd.getEmdDescription.getDcDescription.asScala.map(_.getValue), usr)
      } should matchPattern { case Success((`id`, Seq("descr foo bar"), `user`)) => }
  }
}
