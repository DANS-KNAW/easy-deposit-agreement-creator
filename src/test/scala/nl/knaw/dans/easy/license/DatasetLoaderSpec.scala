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
import javax.naming.directory.Attributes

import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl, MDContainer, Term}
import org.scalamock.scalatest.MockFactory
import rx.lang.scala.Observable
import rx.lang.scala.observers.TestSubscriber

class DatasetLoaderSpec extends UnitSpec with MockFactory {

  trait MockEasyMetadata extends EasyMetadataImpl {
    override def toString(x: String, y: Name): String = ""
    override def toString(x: String, y: Term): String = ""
    override def toString(x: String, y: MDContainer): String = ""
    override def toString(x: String): String = ""
  }

  val fedora = mock[Fedora]
  val ldap = mock[Ldap]
  val emdMock = mock[MockEasyMetadata]

  implicit val parameters = new Parameters(null, null, null, null, null, fedora, ldap)

  "getUserById" should "query the user data from ldap for a given user id" in {
    val user = new EasyUser("id", "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.just(user)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertValue(user)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  it should "fail with a NoSuchElementException if the query to ldap doesn't yield any user data" in {
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.empty

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[EasyUser]()
    loader.getUserById("testID").subscribe(testObserver)

    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }

  it should "fail with an IllegalArgumentException if the query to ldap yields more than one user data object" in {
    val user1 = new EasyUser("id", "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val user2 = user1.copy(email = "mail2")
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects ("testID", *) returning Observable.just(user1, user2)

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
    val user = new EasyUser(depID, "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val expected = new Dataset(id, emdMock, user)

    (fedora.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.just(depID)
    (fedora.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.just(emdMock)
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (depID, *) returning Observable.just(user)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertValue(expected)
    testObserver.assertNoErrors()
    testObserver.assertCompleted()
  }

  it should "fail if more than one depositor was found in the AMD" in {
    val id = "testID"
    val depID1 = "depID"
    val depID2 = "depID"
    val user1 = new EasyUser(depID1, "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")
    val user2 = new EasyUser(depID2, "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")

    (fedora.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.just(depID1, depID2)
    (fedora.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.just(emdMock)
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (depID1, *) returning Observable.just(user1)
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (depID2, *) returning Observable.just(user2)

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertError(classOf[IllegalArgumentException])
    testObserver.assertNotCompleted()
  }

  it should "fail if no AMD was found with the corresponding datasetID" in {
    val id = "testID"

    (fedora.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.empty
    (fedora.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.just(emdMock)
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (*, *) never()

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }

  it should "fail if no EMD was found with the corresponding datasetID" in {
    val id = "testID"
    val depID = "depID"
    val user = new EasyUser(depID, "name", "org", "addr", "pc", "city", "cntr", "phone", "mail")

    (fedora.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.just(depID)
    (fedora.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.empty
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (depID, *) returning Observable.just(user) noMoreThanOnce()

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }

  it should "fail if no user was found with the corresponding depositorID" in {
    val id = "testID"
    val depID = "depID"

    (fedora.getAMD(_: String)(_: InputStream => String)) expects (id, *) returning Observable.just(depID)
    (fedora.getEMD(_: String)(_: InputStream => EasyMetadata)) expects (id, *) returning Observable.just(emdMock)
    (ldap.query(_: DepositorID)(_: Attributes => EasyUser)) expects (depID, *) returning Observable.empty

    val loader = new DatasetLoaderImpl()
    val testObserver = TestSubscriber[Dataset]()
    loader.getDatasetById(id).subscribe(testObserver)

    testObserver.awaitTerminalEvent()
    testObserver.assertNoValues()
    testObserver.assertError(classOf[NoSuchElementException])
    testObserver.assertNotCompleted()
  }
}
