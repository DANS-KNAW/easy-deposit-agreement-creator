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

import java.io.File

import javax.naming.directory.BasicAttributes
import nl.knaw.dans.easy.agreement._
import org.apache.commons.io.{ FileUtils, IOUtils }
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }
import rx.lang.scala.Observable
import rx.lang.scala.observers.TestSubscriber

import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

class DatasetLoaderSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

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

  implicit val parameters: DatabaseParameters = new DatabaseParameters {
    override val fedora: Fedora = fedoraMock
    override val ldap: Ldap = ldapMock
  }

  before {
    new File(getClass.getResource("/datasetloader/").toURI).copyDir(new File(testDir, "datasetloader"))
  }

  after {
    new File(testDir, "datasetloader").deleteDirectory()
  }

  override def afterAll: Unit = testDir.getParentFile.deleteDirectory()

  "getUserById" should "query the user data from ldap for a given user id" in {
    ldapMock.query _ expects "testID" returning Observable.just(userAttributes)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertValue(expectedUser)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  it should "default to an empty String if the field is not available in the attributes" in {
    ldapMock.query _ expects "testID" returning Observable.just(new BasicAttributes)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertValue(EasyUser("", "", "", "", "", "", "", ""))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  it should "fail with a NoSuchElementException if the query to ldap doesn't yield any user data" in {
    ldapMock.query _ expects "testID" returning Observable.empty

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoUserFoundException])
    testObserver.assertNotCompleted()
  }

  it should "fail with an IllegalArgumentException if the query to ldap yields more than one user data object" in {
    ldapMock.query _ expects "testID" returning Observable.just(userAttributes, userAttributes)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[MultipleUsersFoundException])
    testObserver.assertNotCompleted()
  }

  "getDatasetById" should "return the dataset corresponding to the given identifier" in {
    val id = "testID"
    val depID = "depID"
    val user = EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")

    val amdStream = IOUtils.toInputStream(<foo><depositorId>{depID}</depositorId></foo>.toString)
    val emdStream = FileUtils.openInputStream(new File(testDir, "datasetloader/emd.xml"))

    fedoraMock.getAMD _ expects id returning Observable.just(amdStream)
    fedoraMock.getEMD _ expects id returning Observable.just(emdStream)

    val loader: DatasetLoaderImpl = new DatasetLoaderImpl {
      override def getUserById(depositorID: DepositorID): Observable[EasyUser] = {
        if (depositorID == depID) Observable.just(user)
        else fail(s"not the correct depositorID, was $depositorID, should be $depID")
      }
    }
    val testObserver = TestSubscriber[(DatasetID, Seq[String], EasyUser)]()
    loader.getDatasetById(id)
      // there is no equals defined for the emd, so I need to unpack here
      .map { case Dataset(datasetID, emd, usr) =>
        (datasetID, emd.getEmdDescription.getDcDescription.asScala.map(_.getValue), usr)
      }
      .subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue((id, Seq("descr foo bar"), user))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }
}
