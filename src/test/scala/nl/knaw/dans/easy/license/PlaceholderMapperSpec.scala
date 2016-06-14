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

import java.io.File
import java.{util => ju}

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.pf.language.emd.Term.{Name, Namespace}
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.{IsoDate, MetadataItem}
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

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
    null, null, false, fedora, null)

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

    res.get should contain (IsSample, false)
    res.get should contain (DansManagedDoi, "12.3456/dans-ab7-cdef")
    res.get should contain (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef")
    res.get should contain (DateSubmitted, "1992-07-30")
    res.get should contain (Title, "my preferred title")

    res.get should have size 5
  }

  it should "yield a map with default values if the actual values are null" in {
    emd.getEmdIdentifier _ expects () returning ident
    ident.getDansManagedDoi _ expects () returning null
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = testInstance.header(emd)

    res.get should contain (IsSample, false)
    res.get should contain (DansManagedDoi, "")
    res.get should contain (DansManagedEncodedDoi, "")
    res.get should contain (DateSubmitted, new IsoDate().toString)
    res.get should contain (Title, "my preferred title")

    res.get should have size 5
  }

  "sampleHeader" should "yield a map of the date and title" in {
    implicit val parameters = new Parameters(null, new File(testDir, "placeholdermapper"), null,
      null, null, true, fedora, null)
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () never()
    ident.getDansManagedDoi _ expects () never()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning dates
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = testInstance.sampleHeader(emd)

    res.get should contain (IsSample, true)
    res.get should contain (DateSubmitted, "1992-07-30")
    res.get should contain (Title, "my preferred title")

    res.get should have size 3
  }

  it should "yield a map with default values if the actual values are null" in {
    implicit val parameters = new Parameters(null, new File(testDir, "placeholdermapper"), null,
      null, null, true, fedora, null)

    emd.getEmdIdentifier _ expects () never()
    ident.getDansManagedDoi _ expects () never()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = testInstance.sampleHeader(emd)

    res.get should contain (IsSample, true)
    res.get should contain (DateSubmitted, new IsoDate().toString)
    res.get should contain (Title, "my preferred title")

    res.get should have size 3
  }

  "footerText" should "return the text in a file without its line endings" in {
    testInstance.footerText(new File(parameters.templateDir, "FooterTextTest.txt")) shouldBe "hello\nworld"
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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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

    val res = testInstance.datasetAccessCategory(emd).get

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
    val audienceTerm = new Term(Name.AUDIENCE, Namespace.DC)
    val accessrightsTerm = new Term(Name.ACCESSRIGHTS, Namespace.DC)
    val mediumTerm = new Term(Name.MEDIUM, Namespace.DC)
    val abstractTerm = new Term(Name.ABSTRACT, Namespace.EAS)
    val terms = Set(audienceTerm, accessrightsTerm, mediumTerm, abstractTerm).asJava

    val item1 = metadataItemMock("item1")
    val item2 = metadataItemMock("ANONYMOUS_ACCESS")
    val item3 = metadataItemMock("OPEN_ACCESS")
    val item4 = metadataItemMock("item4")
    val item5 = metadataItemMock("item5")
    val item6 = metadataItemMock("item6")

    val audienceItems = List(item1, item2).asJava
    val accessrightItems = List(item2, item3).asJava
    val mediumItems = List(item4, item5, item6).asJava
    val abstractItems = ju.Collections.emptyList[MetadataItem]()

    emd.getTerms _ expects () returning terms
    emd.getTerm _ expects audienceTerm returning audienceItems
    emd.getTerm _ expects accessrightsTerm returning accessrightItems
    emd.getTerm _ expects mediumTerm returning mediumItems
    emd.getTerm _ expects abstractTerm returning abstractItems

    val expected = List(
      Map(MetadataKey.keyword -> "abc", MetadataValue.keyword -> "abc; def").asJava,
      Map(MetadataKey.keyword -> "def", MetadataValue.keyword -> "Anonymous").asJava,
      Map(MetadataKey.keyword -> "ghi", MetadataValue.keyword -> "item4, item5, item6").asJava
    ).asJava

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234") shouldBe expected
  }

  "formatAudience" should "combine the audiences separated by <semicolon>" in {
    testInstance.formatAudience(Seq("abc", "def"), "datasetID:1234") shouldBe "abc; def"
  }

  it should "do the same with one element in the audience" in {
    testInstance.formatAudience(Seq("abc"), "datasetID:1234") shouldBe "abc"
  }

  it should "yield an Observable with one empty String when there is no audience" in {
    testInstance.formatAudience(Seq.empty, "datasetID:1234") shouldBe ""
  }

  "formatAccessRights" should "return a String representation of the access category ANONYMOUS_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("ANONYMOUS_ACCESS")) shouldBe "Anonymous"
  }

  it should "return a String representation of the access category OPEN_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("OPEN_ACCESS")) shouldBe "Open Access"
  }

  it should "return a String representation of the access category OPEN_ACCESS_FOR_REGISTERED_USERS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("OPEN_ACCESS_FOR_REGISTERED_USERS")) shouldBe "Open access for registered users"
  }

  it should "return a String representation of the access category GROUP_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("GROUP_ACCESS")) shouldBe "Restricted - 'archaeology' group"
  }

  it should "return a String representation of the access category REQUEST_PERMISSION" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("REQUEST_PERMISSION")) shouldBe "Restricted - request permission"
  }

  it should "return a String representation of the access category ACCESS_ELSEWHERE" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("ACCESS_ELSEWHERE")) shouldBe "Elsewhere"
  }

  it should "return a String representation of the access category NO_ACCESS" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("NO_ACCESS")) shouldBe "Other"
  }

  it should "return a String representation of the access category FREELY_AVAILABLE" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("FREELY_AVAILABLE")) shouldBe "Open Access"
  }

  it should "return a String representation of an unknown access category" in {
    testInstance.formatDatasetAccessRights(metadataItemMock("test")) shouldBe "test"
  }

  "formatFileAccessRights" should "return a String representation of the file access category ANONYMOUS" in {
    testInstance.formatFileAccessRights(FileAccessRight.ANONYMOUS) shouldBe "Anonymous"
  }

  it should "return a String representation of the file access category KNOWN" in {
    testInstance.formatFileAccessRights(FileAccessRight.KNOWN) shouldBe "Known"
  }

  it should "return a String representation of the file access category RESTRICTED_REQUEST" in {
    testInstance.formatFileAccessRights(FileAccessRight.RESTRICTED_REQUEST) shouldBe "Restricted request"
  }

  it should "return a String representation of the file access category RESTRICTED_GROUP" in {
    testInstance.formatFileAccessRights(FileAccessRight.RESTRICTED_GROUP) shouldBe "Restricted group"
  }

  it should "return a String representation of the file access category NONE" in {
    testInstance.formatFileAccessRights(FileAccessRight.NONE) shouldBe "None"
  }

  "filesTable" should "give a mapping of files and checksums in the dataset" in {
    val input = Seq(
      FileItem("ABC", FileAccessRight.ANONYMOUS, "123"),
      FileItem("DEF", FileAccessRight.KNOWN, "none"),
      FileItem("GHI", FileAccessRight.RESTRICTED_GROUP, "")
    )

    val expected = List(
      Map(FilePath.keyword -> "ABC", FileChecksum.keyword -> "123", FileAccessibleTo.keyword -> "Anonymous").asJava,
      Map(FilePath.keyword -> "DEF", FileChecksum.keyword -> checkSumNotCalculated, FileAccessibleTo.keyword -> "Known").asJava,
      Map(FilePath.keyword -> "GHI", FileChecksum.keyword -> checkSumNotCalculated, FileAccessibleTo.keyword -> "Restricted group").asJava
    ).asJava

    testInstance.filesTable(input) shouldBe expected
  }
}
