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

import java.io.InputStream
import java.util
import javax.naming.directory.Attributes

import com.yourmediashelf.fedora.generated.management.DatastreamProfile
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import org.scalamock.scalatest.MockFactory
import rx.lang.scala.Observable
import rx.lang.scala.observers.TestSubscriber

import scala.language.reflectiveCalls

class DatasetLoaderSpec extends UnitSpec with MockFactory {

  trait MockEasyMetadata extends EasyMetadataImpl {
    override def toString(x: String, y: Name): String = ""
    override def toString(x: String, y: Term): String = ""
    override def toString(x: String, y: MDContainer): String = ""
    override def toString(x: String): String = ""
  }

  val fedoraMock = mock[Fedora]
  val ldapMock = mock[Ldap]
  val emdMock = mock[MockEasyMetadata]

  implicit val parameters = new DatabaseParameters {
    val fedora: Fedora = fedoraMock
    val ldap: Ldap = ldapMock
  }

  "getUserById" should "query the user data from ldap for a given user id" in {
    val user = new EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    (ldapMock.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.just(user)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertValue(user)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  it should "fail with a NoSuchElementException if the query to ldap doesn't yield any user data" in {
    (ldapMock.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.empty

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }

  it should "fail with an IllegalArgumentException if the query to ldap yields more than one user data object" in {
    val user1 = new EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val user2 = user1.copy(email = "mail2")
    (ldapMock.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.just(user1, user2)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[IllegalArgumentException])
    testObserver.assertNotCompleted()
  }

  "getDatasetById" should "return the dataset corresponding to the given identifier" in {
    val id = "testID"
    val depID = "depID"
    val user = new EasyUser("name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val audience = mock[EmdAudience]
    val audiences = Seq("aud1", "aud2")
    val files = Seq(
      new FileItem("path1", FileAccessRight.RESTRICTED_GROUP, "chs1"),
      new FileItem("path2", FileAccessRight.KNOWN, "chs2")
    )
    val expected = Dataset(id, emdMock, user, audiences, files)

    (fedoraMock.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.just(depID)
    (fedoraMock.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.just(emdMock)
    emdMock.getEmdAudience _ expects () returning audience

    val loader = new DatasetLoaderImpl() {
      override def getUserById(depositorID: DepositorID): Observable[EasyUser] = {
        if (depositorID == depID) Observable.just(user)
        else fail(s"not the correct depositorID, was $depositorID, should be $depID")
      }

      override def getAudiences(a: EmdAudience): Observable[AudienceTitle] = {
        if (a == audience) Observable.from(audiences)
        else fail("not the correct audiences")
      }

      override def getFilesInDataset(did: DatasetID): Observable[FileItem] = {
        if (did == id) Observable.from(files)
        else fail(s"not the correct datasetID, was $did, should be $id")
      }
    }
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue(expected)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getFilesInDataset" should "return the files contained in the dataset corresponding to the given datasetID" in {
    val id = "testID"
    val pid1 = "pid1"
    val pid2 = "pid2"
    val fi1@FileItem(path1, accTo1, chcksm1) = FileItem("path1", FileAccessRight.NONE, "chcksm1")
    val fi2@FileItem(path2, accTo2, chcksm2) = FileItem("path2", FileAccessRight.KNOWN, "chcksm2")

    fedoraMock.queryRiSearch _ expects where[String](_ contains id) returning Observable.just(pid1, pid2)
    (fedoraMock.getFileMetadata(_: String)(_: InputStream => (String, FileAccessRight.Value))) expects (pid1, *) returning Observable.just((path1, accTo1))
    (fedoraMock.getFileMetadata(_: String)(_: InputStream => (String, FileAccessRight.Value))) expects (pid2, *) returning Observable.just((path2, accTo2))
    (fedoraMock.getFile(_: String)(_: DatastreamProfile => String)) expects (pid1, *) returning Observable.just(chcksm1)
    (fedoraMock.getFile(_: String)(_: DatastreamProfile => String)) expects (pid2, *) returning Observable.just(chcksm2)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Set[FileItem]]()
    // toSet such that concurrency thingies (order of results) do not matter
    loader.getFilesInDataset(id).toSet.subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValues(Set(fi1, fi2))
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  "getAudience" should "search the title for each audienceID in the EmdAudience in Fedora" in {
    val (id1, title1) = ("id1", "title1")

    (fedoraMock.getDC(_: String)(_: InputStream => String)) expects (id1, *) returning Observable.just(title1)

    val loader = new DatasetLoaderImpl()
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
    val loader = new DatasetLoaderImpl() {

      var counter = 0

      override def getAudience(audienceID: AudienceID) = {
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
