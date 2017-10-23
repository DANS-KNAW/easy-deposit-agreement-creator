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
package nl.knaw.dans.easy.license.internal

import java.io.File
import java.sql.{ Connection, PreparedStatement, ResultSet }
import java.util
import javax.naming.directory.BasicAttributes

import nl.knaw.dans.easy.license._
import nl.knaw.dans.pf.language.emd._
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
  private val fsrdbMock = mock[Connection]

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
    override val fsrdb: Connection = fsrdbMock
    override val fileLimit: Int = 2
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
    val audiences = Seq("aud1", "aud2")
    val files = Seq(
      FileItem("path1", FileAccessRight.RESTRICTED_GROUP, Some("chs1")),
      FileItem("path2", FileAccessRight.KNOWN, Some("chs2"))
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

      override def countFiles(datasetID: DatasetID): Observable[Int] = {
        Observable.just(3)
      }
    }
    val testObserver = TestSubscriber[(DatasetID, Seq[String], EasyUser, Seq[AudienceTitle], Seq[FileItem], Boolean)]()
    loader.getDatasetById(id)
      // there is no equals defined for the emd, so I need to unpack here
      .map { case Dataset(datasetID, emd, usr, titles, fileItems, allFilesListed) =>
        (datasetID, emd.getEmdDescription.getDcDescription.asScala.map(_.getValue), usr, titles, fileItems, allFilesListed) }
      .subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue((id, Seq("descr foo bar"), user, audiences, files, true))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getFilesInDataset" should "return the files contained in the dataset corresponding to the given datasetID" in {
    val id = "testID"
    val pid1 = "pid1"
    val pid2 = "pid2"
    val fi1 @ FileItem(path1, accTo1, Some(chcksm1)) = FileItem("path1", FileAccessRight.NONE, Some("chcksm1"))
    val fi2 @ FileItem(path2, accTo2, _) = FileItem("path2", FileAccessRight.KNOWN, None)

    val mockedPrepStatement = mock[PreparedStatement]
    val mockedResultSet = mock[ResultSet]

    inSequence {
      (fsrdbMock.prepareStatement(_: String)) expects * returning mockedPrepStatement
      mockedPrepStatement.setString _ expects(1, id)
      mockedPrepStatement.setInt _ expects(2, 2)
      mockedPrepStatement.executeQuery _ expects() returning mockedResultSet
      mockedResultSet.next _ expects() returning true
      (mockedResultSet.getString(_: String)) expects "pid" returning pid1
      (mockedResultSet.getString(_: String)) expects "path" returning path1
      (mockedResultSet.getString(_: String)) expects "sha1checksum" returning chcksm1
      (mockedResultSet.getString(_: String)) expects "accessible_to" returning accTo1.toString
      mockedResultSet.next _ expects() returning true
      (mockedResultSet.getString(_: String)) expects "pid" returning pid2
      (mockedResultSet.getString(_: String)) expects "path" returning path2
      (mockedResultSet.getString(_: String)) expects "sha1checksum" returning "null"
      (mockedResultSet.getString(_: String)) expects "accessible_to" returning accTo2.toString
      mockedResultSet.next _ expects() returning false
      mockedResultSet.close _ expects()
      mockedPrepStatement.close _ expects()
    }

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[Set[FileItem]]()
    loader.getFilesInDataset(id).toSet.subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue(Set(fi1, fi2))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "countFiles" should "return the number of files contained in the dataset corresponding to the given datasetID" in {
    val id = "testID"
    val fileCount = 500

    val mockedPrepStatement = mock[PreparedStatement]
    val mockedResultSet = mock[ResultSet]

    inSequence {
      (fsrdbMock.prepareStatement(_: String)) expects * returning mockedPrepStatement
      mockedPrepStatement.setString _ expects(1, id)
      mockedPrepStatement.executeQuery _ expects() returning mockedResultSet
      mockedResultSet.next _ expects() returning true
      (mockedResultSet.getInt(_: String)) expects "count" returning fileCount
      mockedResultSet.close _ expects()
      mockedPrepStatement.close _ expects()
    }

    val loader = DatasetLoaderImpl()
    val testObserver = TestSubscriber[Int]()
    loader.countFiles(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue(fileCount)
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

      override def getAudience(audienceID: AudienceID): Observable[AudienceID] = {
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
