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

import java.io.{File, InputStream}
import java.{util => ju}

import com.yourmediashelf.fedora.generated.management.DatastreamProfile
import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.pf.language.emd.Term.{Name, Namespace}
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.{IsoDate, MetadataItem}
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}
import rx.lang.scala.Observable
import rx.lang.scala.observers.TestSubscriber

import scala.collection.JavaConverters._

class PlaceholderMapperSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""
    def toString(x: String, y: Term): String = ""
    def toString(x: String, y: MDContainer): String = ""
    def toString(x: String): String = ""
  }

  val emd = mock[MockEasyMetadata]
  val ident = mock[EmdIdentifier]
  val date = mock[EmdDate]
  val rights = mock[EmdRights]
  val fedora = mock[Fedora]

  implicit val parameters = new Parameters(null, new File(testDir, "placeholdermapper"), null,
    null, null, fedora, null)

  before {
    new File(getClass.getResource("/placeholdermapper/").toURI).copyDir(parameters.templateDir)
  }

  after {
    parameters.templateDir.deleteDirectory()
  }

  override def afterAll = testDir.getParentFile.deleteDirectory()

  def testInstance = {
    new PlaceholderMapper(new File(parameters.templateDir, "MetadataTestTerms.properties"))
  }

  def metadataItemMock(s: String): MetadataItem = {
    new MetadataItem {
      def getSchemeId = throw new NotImplementedError()
      def isComplete = throw new NotImplementedError()
      override def toString = s
    }
  }

  "header" should "yield a map of the DOI, date and title" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () returning ident
    ident.getDansManagedDoi _ expects () returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning dates
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = testInstance.header(emd)

    res should contain (DansManagedDoi, "12.3456/dans-ab7-cdef")
    res should contain (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef")
    res should contain (DateSubmitted, "1992-07-30")
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  it should "yield a map with default values if the actual values are null" in {
    emd.getEmdIdentifier _ expects () returning ident
    ident.getDansManagedDoi _ expects () returning null
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = testInstance.header(emd)

    res should contain (DansManagedDoi, "")
    res should contain (DansManagedEncodedDoi, "")
    res should contain (DateSubmitted, new IsoDate().toString)
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  "footerText" should "return the text in a file without its line endings" in {
    val file = new File(parameters.templateDir, "FooterTextTest.txt")

    val res = testInstance.footerText(file)

    res shouldBe "hello\nworld"
  }

  "getDate" should "return the first IsoDate from the list generated in the function when this list is not empty" in {
    val isoDate1 = new IsoDate()
    val isoDate2 = new IsoDate()

    emd.getEmdDate _ expects () returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Arrays.asList(isoDate1, isoDate2)
    })

    res should contain (isoDate1)
  }

  it should "return no IsoDate when the list is empty" in {
    emd.getEmdDate _ expects () returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Collections.emptyList()
    })

    res shouldBe empty
  }

  "depositor" should "yield a map with depositor data" in {
    val depositor = new EasyUser("uid", "name", "org", "addr", "postal", "city", "country", "tel", "mail")

    val res = testInstance.depositor(depositor)

    res should contain (DepositorName -> "name")
    res should contain (DepositorOrganisation -> "org")
    res should contain (DepositorAddress -> "addr")
    res should contain (DepositorPostalCode -> "postal")
    res should contain (DepositorCity -> "city")
    res should contain (DepositorCountry -> "country")
    res should contain (DepositorTelephone -> "tel")
    res should contain (DepositorEmail -> "mail")

    res should have size 8
  }

  "accessRights" should "map an Open Access category to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.OPEN_ACCESS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, true)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map an Anonymous Access category to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.ANONYMOUS_ACCESS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, true)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a Freely Available category to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.FREELY_AVAILABLE

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, true)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map an Open Access For Registered Users category to an OpenAccessForRegisteredUsers keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, true)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a Group Access category to an RestrictGroup keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.GROUP_ACCESS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, true)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a Request Permission category to an RestrictRequest keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.REQUEST_PERMISSION

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, true)

    res should have size 5
  }

  it should "map an Access Elsewhere category to an OtherAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.ACCESS_ELSEWHERE

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, true)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a No Access category to an OtherAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.NO_ACCESS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, true)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a null value to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.OPEN_ACCESS

    val res = testInstance.accessRights(emd).get

    res should contain (OpenAccess, true)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  "embargo" should "give the embargo keyword mappings with UnderEmbargo=true when there is an embargo" in {
    val nextYear = new DateTime().plusYears(1)
    val dates = ju.Arrays.asList(new IsoDate(nextYear), new IsoDate("1992-07-30"))

    emd.getEmdDate _ expects () returning date
    date.getEasAvailable _ expects () returning dates

    val res = testInstance.embargo(emd)

    res should contain (UnderEmbargo, true)
    res should contain (DateAvailable, nextYear.toString("YYYY-MM-dd"))

    res should have size 2
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when there is no embargo" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdDate _ expects () returning date
    date.getEasAvailable _ expects () returning dates

    val res = testInstance.embargo(emd)

    res should contain (UnderEmbargo, false)
    res should contain (DateAvailable, "1992-07-30")

    res should have size 2
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when no DateAvailable is available" in {
    emd.getEmdDate _ expects () returning date
    date.getEasAvailable _ expects () returning ju.Collections.emptyList()

    val res = testInstance.embargo(emd)

    res should contain (UnderEmbargo, false)
    res should contain (DateAvailable, "")

    res should have size 2
  }

  "metadataTable" should "give a mapping for the metadata elements in the dataset" in {
    val term1 = new Term(Name.AUDIENCE, Namespace.DC)
    val term2 = new Term(Name.ACCESSRIGHTS, Namespace.DC)
    val term3 = new Term(Name.MEDIUM, Namespace.DC)
    val term4 = new Term(Name.ABSTRACT, Namespace.EAS)
    val terms = Set(term1, term2, term3, term4).asJava

    val item1 = metadataItemMock("item1")
    val item2 = metadataItemMock("item2")
    val item3 = metadataItemMock("item3")
    val item4 = metadataItemMock("item4")
    val item5 = metadataItemMock("item5")

    val items1 = List(item1, item2).asJava
    val items2 = List(item2, item3).asJava
    val items3 = List(item3, item4, item5).asJava
    val items4 = ju.Collections.emptyList[MetadataItem]()

    emd.getTerms _ expects () returning terms
    emd.getTerm _ expects term1 returning items1
    emd.getTerm _ expects term2 returning items2
    emd.getTerm _ expects term3 returning items3
    emd.getTerm _ expects term4 returning items4

    val testInstance = new PlaceholderMapper(new File(parameters.templateDir, "MetadataTestTerms.properties")) {
      override def formatAccessRights(item: MetadataItem) = {
        "test123"
      }

      override def formatAudience(emd: EasyMetadata) = {
        Observable.just("audience")
      }
    }

    val expected = List(
      Map(MetadataKey.keyword -> "abc", MetadataValue.keyword -> "audience").asJava,
      Map(MetadataKey.keyword -> "def", MetadataValue.keyword -> "test123").asJava,
      Map(MetadataKey.keyword -> "ghi", MetadataValue.keyword -> "item3, item4, item5").asJava
    ).asJava

    val testSubscriber = TestSubscriber[ju.List[ju.Map[String, String]]]()
    testInstance.metadataTable(emd).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValue(expected)
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }

  "formatAudience" should "retrieve the audience identifier from the EMD, " +
    "look for the title of this identifier in Fedora's DC " +
    "and return the combination of the results separated by <semicolon>" in {
    val audience = mock[EmdAudience]

    emd.getEmdAudience _ expects () returning audience
    audience.getValues _ expects () returning ju.Arrays.asList("abc", "def")
    emd.getPreferredTitle _ expects () never()

    inSequence {
      (fedora.getDC(_: String)(_: InputStream => String)) expects ("abc", *) returning Observable.just("ABC")
      (fedora.getDC(_: String)(_: InputStream => String)) expects ("def", *) returning Observable.just("DEF")
    }

    val testSubscriber = TestSubscriber[String]()
    testInstance.formatAudience(emd).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValue("ABC; DEF")
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }

  it should "do the same with one element in the audience" in {
    val audience = mock[EmdAudience]

    emd.getEmdAudience _ expects () returning audience once()
    audience.getValues _ expects () returning ju.Arrays.asList("abc") once()
    emd.getPreferredTitle _ expects () never()

    (fedora.getDC(_: String)(_: InputStream => String)) expects ("abc", *) returning Observable.just("ABC")

    val testSubscriber = TestSubscriber[String]()
    testInstance.formatAudience(emd).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValue("ABC")
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }

  it should "yield an Observable with one empty String when there is no audience" in {
    val audience = mock[EmdAudience]

    emd.getEmdAudience _ expects () returning audience
    audience.getValues _ expects () returning ju.Collections.emptyList[String]
    emd.getPreferredTitle _ expects () returning "wie dit leest is een aap" once()

    val testSubscriber = TestSubscriber[String]()
    testInstance.formatAudience(emd).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValue("")
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }

  "formatAccessRights" should "return a String representation of the access category ANONYMOUS_ACCESS" in {
    val mockItem = metadataItemMock("ANONYMOUS_ACCESS")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Anonymous"
  }

  it should "return a String representation of the access category OPEN_ACCESS" in {
    val mockItem = metadataItemMock("OPEN_ACCESS")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Open Access"
  }

  it should "return a String representation of the access category OPEN_ACCESS_FOR_REGISTERED_USERS" in {
    val mockItem = metadataItemMock("OPEN_ACCESS_FOR_REGISTERED_USERS")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Open access for registered users"
  }

  it should "return a String representation of the access category GROUP_ACCESS" in {
    val mockItem = metadataItemMock("GROUP_ACCESS")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Restricted - 'archaeology' group"
  }

  it should "return a String representation of the access category REQUEST_PERMISSION" in {
    val mockItem = metadataItemMock("REQUEST_PERMISSION")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Restricted - request permission"
  }

  it should "return a String representation of the access category ACCESS_ELSEWHERE" in {
    val mockItem = metadataItemMock("ACCESS_ELSEWHERE")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Elsewhere"
  }

  it should "return a String representation of the access category NO_ACCESS" in {
    val mockItem = metadataItemMock("NO_ACCESS")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Other"
  }

  it should "return a String representation of the access category FREELY_AVAILABLE" in {
    val mockItem = metadataItemMock("FREELY_AVAILABLE")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "Open Access"
  }

  it should "return a String representation of an unknown access category" in {
    val mockItem = metadataItemMock("test")

    val res = testInstance.formatAccessRights(mockItem)

    res shouldBe "test"
  }

  "filesTable" should "give a mapping of files and checksums in the dataset" in {
    inSequence {
      fedora.queryRiSearch _ expects * returning Observable.just("abc", "def", "ghi")
      (fedora.getFileMetadata(_: String)(_: InputStream => String)) expects ("abc", *) returning Observable.just("ABC")
      (fedora.getFile(_: String)(_: DatastreamProfile => String)) expects ("abc", *) returning Observable.just("123")
      (fedora.getFileMetadata(_: String)(_: InputStream => String)) expects ("def", *) returning Observable.just("DEF")
      (fedora.getFile(_: String)(_: DatastreamProfile => String)) expects ("def", *) returning Observable.just("none")
      (fedora.getFileMetadata(_: String)(_: InputStream => String)) expects ("ghi", *) returning Observable.just("GHI")
      (fedora.getFile(_: String)(_: DatastreamProfile => String)) expects ("ghi", *) returning Observable.just("")
    }

    // due to concurrency we cannot determine the order of the results; therefore we use a set here
    val expected = Set(
      Map(FileKey.keyword -> "ABC", FileValue.keyword -> "123").asJava,
      Map(FileKey.keyword -> "DEF", FileValue.keyword -> checkSumNotCalculated).asJava,
      Map(FileKey.keyword -> "GHI", FileValue.keyword -> checkSumNotCalculated).asJava
    ).asJava

    val testSubscriber = TestSubscriber[ju.Set[ju.Map[String, String]]]()
    testInstance.filesTable("foobar").map(list => {
      val set = new ju.HashSet[ju.Map[String, String]]
      set.addAll(list)
      set
    }).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValue(expected)
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }
}
