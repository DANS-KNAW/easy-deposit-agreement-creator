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
package nl.knaw.dans.easy.license.internal

import java.io.File
import java.util
import javax.naming.directory.BasicAttributes

import com.yourmediashelf.fedora.generated.management.DatastreamProfile
import nl.knaw.dans.easy.license._
import nl.knaw.dans.pf.language.emd._
import org.apache.commons.io.{FileUtils, IOUtils}
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import rx.lang.scala.Observable
import rx.lang.scala.observers.TestSubscriber

import scala.collection.JavaConverters._
import scala.language.reflectiveCalls

class DatasetLoaderSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

  val fedoraMock = mock[Fedora]
  val ldapMock = mock[Ldap]

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

  implicit val parameters = new DatabaseParameters {
    val fedora: Fedora = fedoraMock
    val ldap: Ldap = ldapMock
  }

  before {
    new File(getClass.getResource("/datasetloader/").toURI).copyDir(new File(testDir, "datasetloader"))
  }

  after {
    new File(testDir, "datasetloader").deleteDirectory()
  }

  override def afterAll = testDir.getParentFile.deleteDirectory()

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
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }

  it should "fail with an IllegalArgumentException if the query to ldap yields more than one user data object" in {
    ldapMock.query _ expects "testID" returning Observable.just(userAttributes, userAttributes)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[IllegalArgumentException])
    testObserver.assertNotCompleted()
  }

  "getDatasetById" should "return the dataset corresponding to the given identifier" in {
    val id = "testID"
    val depID = "depID"
    val user = EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val audiences = Seq("aud1", "aud2")
    val files = Seq(
      FileItem("path1", FileAccessRight.RESTRICTED_GROUP, "chs1"),
      FileItem("path2", FileAccessRight.KNOWN, "chs2")
    )

    val amdStream = IOUtils.toInputStream(<foo><depositorId>{depID}</depositorId></foo>.toString)
    val emdStream = FileUtils.openInputStream(new File(testDir, "datasetloader/emd.xml"))

    fedoraMock.getAMD _ expects id returning Observable.just(amdStream)
    fedoraMock.getEMD _ expects id returning Observable.just(emdStream)

    val loader = new DatasetLoaderImpl {
      override def getUserById(depositorID: DepositorID): Observable[EasyUser] = {
        if (depositorID == depID) Observable.just(user)
        else fail(s"not the correct depositorID, was $depositorID, should be $depID")
      }

      override def getAudiences(a: EmdAudience): Observable[AudienceTitle] = {
        if (a.getDisciplines.asScala.map(_.getValue) == audiences) Observable.from(audiences)
        else fail("not the correct audiences")
      }

      override def getFilesInDataset(did: DatasetID): Observable[FileItem] = {
        if (did == id) Observable.from(files)
        else fail(s"not the correct datasetID, was $did, should be $id")
      }
    }
    val testObserver = TestSubscriber[(DatasetID, Seq[String], EasyUser, Seq[AudienceTitle], Seq[FileItem])]()
    loader.getDatasetById(id)
      // there is no equals defined for the emd, so I need to unpack here
      .map { case Dataset(datasetID, emd, usr, titles, fileItems) =>
        (datasetID, emd.getEmdDescription.getDcDescription.asScala.map(_.getValue), usr, titles, fileItems) }
      .subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue((id, Seq("descr foo bar"), user, audiences, files))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getFilesInDataset" should "return the files contained in the dataset corresponding to the given datasetID" in {
    val id = "testID"
    val pid1 = "pid1"
    val pid2 = "pid2"
    val fi1@FileItem(path1, accTo1, chcksm1) = FileItem("path1", FileAccessRight.NONE, "chcksm1")
    val fi2@FileItem(path2, accTo2, chcksm2) = FileItem("path2", FileAccessRight.KNOWN, "chcksm2")

    val pa1 = IOUtils.toInputStream(<foo><path>{path1}</path><accessibleTo>{accTo1}</accessibleTo></foo>.toString)
    val pa2 = IOUtils.toInputStream(<foo><path>{path2}</path><accessibleTo>{accTo2}</accessibleTo></foo>.toString)

    val dp1 = mock[DatastreamProfile]
    val dp2 = mock[DatastreamProfile]

    dp1.getDsChecksum _ expects () returning chcksm1
    dp2.getDsChecksum _ expects () returning chcksm2
    fedoraMock.queryRiSearch _ expects where[String](_ contains id) returning Observable.just(pid1, pid2)
    fedoraMock.getFileMetadata _ expects pid1 returning Observable.just(pa1)
    fedoraMock.getFileMetadata _ expects pid2 returning Observable.just(pa2)
    fedoraMock.getFile _ expects pid1 returning Observable.just(dp1)
    fedoraMock.getFile _ expects pid2 returning Observable.just(dp2)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[Set[FileItem]]()
    // toSet such that concurrency thingies (order of results) do not matter
    loader.getFilesInDataset(id).toSet.subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValues(Set(fi1, fi2))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getAudience" should "search the title for each audienceID in the EmdAudience in Fedora" in {
    val is = IOUtils.toInputStream(<foo><title>title1</title></foo>.toString)
    val (id1, title1) = ("id1", "title1")

    fedoraMock.getDC _ expects id1 returning Observable.just(is)

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[String]()
    loader.getAudience(id1).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValues(title1)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getAudiences" should "search the title for each audienceID in the EmdAudience in Fedora" in {
    val (id1, title1) = ("id1", "title1")
    val (id2, title2) = ("id2", "title2")
    val (id3, title3) = ("id3", "title3")
    val audience = mock[EmdAudience]

    audience.getValues _ expects() returning util.Arrays.asList(id1, id2, id3)

    // can't do mocking due to concurrency issues
    val loader = new DatasetLoaderImpl {

      var counter = 0

      override def getAudience(audienceID: AudienceID) =
      {
        counter += 1
        counter match {
          case 1 => Observable.just(title1)
          case 2 => Observable.just(title2)
          case 3 => Observable.just(title3)
          case _ => throw new IllegalStateException(s"Called this method too many times. audienceID = $audienceID")
        }
      }
    }
    val testObserver = TestSubscriber[String]()
    loader.getAudiences(audience).subscribe(testObserver)

    testObserver.assertValues(title1, title2, title3)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
    loader.counter shouldBe 3
  }
}
