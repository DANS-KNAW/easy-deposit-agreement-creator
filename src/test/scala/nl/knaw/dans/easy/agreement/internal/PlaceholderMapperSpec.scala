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
import java.net.URI
import java.{ util => ju }

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.{ FileAccessRight, FileItem, UnitSpec }
import nl.knaw.dans.pf.language.emd.Term.{ Name, Namespace }
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.Spatial.{ Box, Point }
import nl.knaw.dans.pf.language.emd.types._
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{ BeforeAndAfter, BeforeAndAfterAll }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success, Try }

class PlaceholderMapperSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""

    def toString(x: String, y: Term): String = ""

    def toString(x: String, y: MDContainer): String = ""

    def toString(x: String): String = ""
  }

  private val emd = mock[MockEasyMetadata]
  private val ident = mock[EmdIdentifier]
  private val date = mock[EmdDate]
  private val rights = mock[EmdRights]

  implicit val parameters: Parameters = Parameters(
    templateResourceDir = new File(testDir, "placeholdermapper"),
    datasetID = null,
    isSample = false,
    fedora = null,
    ldap = null,
    fsrdb = null,
    fileLimit = 3)

  before {
    new File(getClass.getResource("/placeholdermapper/").toURI).copyDir(parameters.templateResourceDir)
  }

  after {
    parameters.templateResourceDir.deleteDirectory()
  }

  override def afterAll: Unit = testDir.getParentFile.deleteDirectory()

  def testInstance: PlaceholderMapper = {
    new PlaceholderMapper(new File(parameters.templateResourceDir, "MetadataTestTerms.properties"))
  }

  def metadataItemMock(s: String): MetadataItem = {
    new MetadataItem {
      def getSchemeId = throw new NotImplementedError()

      def isComplete = throw new NotImplementedError()

      override def toString: String = s
    }
  }

  "header" should "yield a map of the DOI, date and title" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects() returning ident
    ident.getDansManagedDoi _ expects() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning dates
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.header(emd)) {
      case Success(map) => map should {
        have size 5 and contain allOf(
          (IsSample, false),
          (DansManagedDoi, "12.3456/dans-ab7-cdef"),
          (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef"),
          (DateSubmitted, "1992-07-30"),
          (Title, "my preferred title"))
      }
    }
  }

  it should "yield a map with default values if the actual values are null" in {
    emd.getEmdIdentifier _ expects() returning ident
    ident.getDansManagedDoi _ expects() returning null
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.header(emd)) {
      case Success(map) => map should {
        have size 5 and contain allOf(
          (IsSample, false),
          (DansManagedDoi, ""),
          (DansManagedEncodedDoi, ""),
          (DateSubmitted, new IsoDate().toString),
          (Title, "my preferred title"))
      }
    }
  }

  "sampleHeader" should "yield a map of the date and title" in {
    implicit val parameters: Parameters = Parameters(
      templateResourceDir = new File(testDir, "placeholdermapper"),
      datasetID = null,
      isSample = true,
      fedora = null,
      ldap = null,
      fsrdb = null,
      fileLimit = 3)
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects() never()
    ident.getDansManagedDoi _ expects() never()
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning dates
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.sampleHeader(emd)) {
      case Success(map) => map should {
        have size 3 and contain allOf(
          (IsSample, true),
          (DateSubmitted, "1992-07-30"),
          (Title, "my preferred title"))
      }
    }
  }

  it should "yield a map with default values if the actual values are null" in {
    implicit val parameters: Parameters = Parameters(
      templateResourceDir = new File(testDir, "placeholdermapper"),
      datasetID = null,
      isSample = true,
      fedora = null,
      ldap = null,
      fsrdb = null,
      fileLimit = 3)

    emd.getEmdIdentifier _ expects() never()
    ident.getDansManagedDoi _ expects() never()
    emd.getEmdDate _ expects() returning date
    date.getEasDateSubmitted _ expects() returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects() returning "my preferred title"

    inside(testInstance.sampleHeader(emd)) {
      case Success(map) => map should {
        have size 3 and contain allOf(
          (IsSample, true),
          (DateSubmitted, new IsoDate().toString),
          (Title, "my preferred title"))
      }
    }
  }

  "footerText" should "return the text in a file without its line endings" in {
    testInstance.footerText(new File(parameters.templateResourceDir, "FooterTextTest.txt")) shouldBe "hello\nworld"
  }

  "getDate" should "return the first IsoDate from the list generated in the function when this list is not empty" in {
    val isoDate1 = new IsoDate()
    val isoDate2 = new IsoDate()

    emd.getEmdDate _ expects() returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Arrays.asList(isoDate1, isoDate2)
    })

    res.value shouldBe isoDate1
  }

  it should "return no IsoDate when the list is empty" in {
    emd.getEmdDate _ expects() returning date

    val res = testInstance.getDate(emd)(d => {
      assert(d eq date)
      ju.Collections.emptyList()
    })

    res shouldBe empty
  }

  "depositor" should "yield a map with depositor data" in {
    val depositor = EasyUser("name", "org", "addr", "postal", "city", "country", "tel", "mail")

    testInstance.depositor(depositor) should contain theSameElementsAs List(
      DepositorName -> "name",
      DepositorOrganisation -> "org",
      DepositorAddress -> "addr",
      DepositorPostalCode -> "postal",
      DepositorCity -> "city",
      DepositorCountry -> "country",
      DepositorTelephone -> "tel",
      DepositorEmail -> "mail")
  }

  "accessRights" should "map an Open Access category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.OPEN_ACCESS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, true),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map an Anonymous Access category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.ANONYMOUS_ACCESS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, true),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map a Freely Available category to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.FREELY_AVAILABLE)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, true),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map an Open Access For Registered Users category to an OpenAccessForRegisteredUsers keyword" in {
    expectEmdRights(AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, false),
        (OpenAccessForRegisteredUsers, true),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map a Group Access category to an RestrictGroup keyword" in {
    expectEmdRights(AccessCategory.GROUP_ACCESS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, false),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, true),
        (RestrictRequest, false))
    }
  }

  it should "map a Request Permission category to an RestrictRequest keyword" in {
    expectEmdRights(AccessCategory.REQUEST_PERMISSION)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, false),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, true))
    }
  }

  it should "map an Access Elsewhere category to an OtherAccess keyword" in {
    expectEmdRights(AccessCategory.ACCESS_ELSEWHERE)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, false),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, true),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map a No Access category to an OtherAccess keyword" in {
    expectEmdRights(AccessCategory.NO_ACCESS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, false),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, true),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  it should "map a null value to an OpenAccess keyword" in {
    expectEmdRights(AccessCategory.OPEN_ACCESS)

    inside(testInstance.datasetAccessCategory(emd)) {
      case Success(map) => map should contain theSameElementsAs List(
        (OpenAccess, true),
        (OpenAccessForRegisteredUsers, false),
        (OtherAccess, false),
        (RestrictGroup, false),
        (RestrictRequest, false))
    }
  }

  "embargo" should "give the embargo keyword mappings with UnderEmbargo=true when there is an embargo" in {
    val nextYear = new DateTime().plusYears(1)
    val dates = ju.Arrays.asList(new IsoDate(nextYear), new IsoDate("1992-07-30"))

    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning dates

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, true),
      (DateAvailable, nextYear.toString("YYYY-MM-dd")))
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when there is no embargo" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning dates

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, false),
      (DateAvailable, "1992-07-30")
    )
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when no DateAvailable is available" in {
    emd.getEmdDate _ expects() returning date
    date.getEasAvailable _ expects() returning ju.Collections.emptyList()

    testInstance.embargo(emd) should contain theSameElementsAs List(
      (UnderEmbargo, false),
      (DateAvailable, "")
    )
  }

  "metadataTable" should "give a mapping for the metadata elements in the dataset" in {
    val ccBy = "https://creativecommons.org/licenses/by/4.0/legalcode"

    val audienceTerm = new Term(Name.AUDIENCE, Namespace.DCTERMS)
    val mediumTerm = new Term(Name.MEDIUM, Namespace.DC)
    val abstractTerm = new Term(Name.ABSTRACT, Namespace.EAS)
    val terms = Set(audienceTerm, accessRightsTerm, licenseTerm, mediumTerm, abstractTerm).asJava

    val anonymous = metadataItemMock("ANONYMOUS_ACCESS")
    val open = metadataItemMock("OPEN_ACCESS")
    val item1 = metadataItemMock("item1")
    val item4 = metadataItemMock("item4")
    val item5 = metadataItemMock("item5")
    val item6 = metadataItemMock("item6")

    val audienceItems = List(item1, anonymous).asJava
    val accessRightItems = List(anonymous, open).asJava
    val mediumItems = List(item4, item5, item6).asJava
    val abstractItems = ju.Collections.emptyList[MetadataItem]()

    emd.getTerms _ expects() returning terms
    emd.getTerm _ expects audienceTerm returning audienceItems
    emd.getTerm _ expects accessRightsTerm returning accessRightItems
    emd.getTerm _ expects mediumTerm returning mediumItems
    emd.getTerm _ expects abstractTerm returning abstractItems

    expectLicenses(Seq("accept", ccBy))

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala.toList shouldBe List(
      Map(MetadataKey.keyword -> "ghi", MetadataValue.keyword -> "item4<br/>item5<br/>item6").asJava,
      Map(MetadataKey.keyword -> "Access rights", MetadataValue.keyword -> "Anonymous").asJava,
      Map(MetadataKey.keyword -> "License", MetadataValue.keyword -> ccBy).asJava,
      Map(MetadataKey.keyword -> "Audience", MetadataValue.keyword -> "abc; def").asJava,
    )
  }

  it should "fail on currently invalid input" in {
    val audienceTerm = new Term(Name.AUDIENCE, Namespace.DC)
    val accessRightsTerm = new Term(Name.ACCESSRIGHTS, Namespace.DC) // should be dcterms
    val items = List(metadataItemMock("ANONYMOUS_ACCESS")).asJava
    emd.getTerms _ expects() returning Set(audienceTerm, accessRightsTerm).asJava
    emd.getTerm _ expects audienceTerm returning items
    emd.getTerm _ expects accessRightsTerm returning items

    // TODO fix scala.MatchError
    Try(testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234")) should matchPattern {
      case Failure(e) if e.getMessage == "((AUDIENCE,abc; def),(ACCESSRIGHTS,Anonymous)) (of class scala.Tuple2)" =>
    }
  }

  it should "give a mapping with a cc0 license" in {
    emd.getTerms _ expects() returning Set(licenseTerm, accessRightsTerm).asJava
    expectLicenses(Seq("accept"))
    expectEmdRights(AccessCategory.OPEN_ACCESS)
    expectRightsTerms(AccessCategory.OPEN_ACCESS)

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala should contain
    Map(
      MetadataKey.keyword -> "license",
      MetadataValue.keyword -> "http://creativecommons.org/publicdomain/zero/1.0/legalcode",
    ).asJava
  }

  it should "give a mapping with a DANS license" in {
    emd.getTerms _ expects() returning Set(licenseTerm, accessRightsTerm).asJava
    expectLicenses(Seq("accept"))
    expectEmdRights(AccessCategory.GROUP_ACCESS)
    expectRightsTerms(AccessCategory.GROUP_ACCESS)

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala should contain
    Map(
      MetadataKey.keyword -> "license",
      MetadataValue.keyword -> "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSGeneralconditionsofuseUKDEF.pdf",
    ).asJava
  }

  it should "map an empty license list to a default license" in {
    emd.getTerms _ expects() returning Set(licenseTerm, accessRightsTerm).asJava
    val items: Seq[MetadataItem] = Seq[MetadataItem]()
    emd.getTerm _ expects licenseTerm returning items.asJava

    expectRightsTerms(AccessCategory.REQUEST_PERMISSION)

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala should contain
    Map(
      MetadataKey.keyword -> "license",
      MetadataValue.keyword -> "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSGeneralconditionsofuseUKDEF.pdf",
    ).asJava
  }

  it should "map just access rights to a default license" in {
    emd.getTerms _ expects() returning Set(accessRightsTerm).asJava
    expectRightsTerms(AccessCategory.OPEN_ACCESS)

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala should contain
    Map(
      MetadataKey.keyword -> "License",
      MetadataValue.keyword -> "http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSGeneralconditionsofuseUKDEF.pdf",
    ).asJava
  }

  it should "map the specified license" in {
    val ccBy = "https://creativecommons.org/licenses/by/4.0/legalcode"
    emd.getTerms _ expects() returning Set(licenseTerm).asJava
    expectLicenses(Seq(ccBy))

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala.toSeq shouldBe Seq(
      Map(
        MetadataKey.keyword -> "License",
        MetadataValue.keyword -> ccBy,
      ).asJava,
    )
  }

  it should "map relations" in {
    val term = new Term(Name.RELATION, Namespace.DC)
    emd.getTerms _ expects() returning Set(term).asJava
    emd.getTerm _ expects term returning Seq(
      new BasicString("foo"),
      new Relation("bar"),
      new Relation("rabarbera", new URI("http://xx.dans.knaw.nl/yy")),
    ).asJava

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala.toSeq shouldBe Seq(
      Map(
        MetadataKey.keyword -> "Relation",
        MetadataValue.keyword -> Seq(
          "foo",
          "title = bar",
          "title = rabarbera, url = http://xx.dans.knaw.nl/yy"
        ).mkString("<br/>"),
      ).asJava,
    )
  }

  it should "map spatials" in {
    val exterior = new PolygonPart("main triangle", ju.Arrays.asList(
      new PolygonPoint("52.08110", "4.34521"),
      new PolygonPoint("52.08071", "4.34422"),
      new PolygonPoint("52.07913", "4.34332"),
      new PolygonPoint("52.08110", "4.34521")
    ))
    val term = new Term(Name.SPATIAL, Namespace.DCTERMS)
    emd.getTerms _ expects() returning Set(term).asJava
    emd.getTerm _ expects term returning Seq(
      new BasicString("foo"),
      new Spatial("Amsterdam", new Point("RD", "1", "2")),
      new Spatial("Amsterdam", new Box("RD", "463001", "155001", "462999", "154999")),
      new Spatial("foo", new Polygon("bar", exterior, List().asJava)),
    ).asJava

    testInstance.metadataTable(emd, Seq("abc", "def"), "datasetID:1234").asScala.toSeq shouldBe Seq(
      Map(
        MetadataKey.keyword -> "Spatial",
        MetadataValue.keyword -> Seq(
          "foo",
          "Amsterdam<br/><b>Point</b>: scheme = RD, x = 1, y = 2",
          "Amsterdam<br/><b>Box:</b> scheme = RD, north = 463001, east = 155001, south = 462999, west = 154999",
          """foo<br/><b>Polygon:</b><br/><i>To keep this agreement at a reasonable size the polygon coordinates are omitted. For a full listing of the polygons please contact DANS at <a href="mailto:info@dans.knaw.nl">info@dans.knaw.nl</a>.</i>""",
        ).mkString("<br/><br/>"),
      ).asJava,
    )
  }

  private val licenseTerm = new Term(Name.LICENSE, Namespace.DCTERMS)

  private def expectLicenses(values: Seq[String]) = {
    val items: Seq[MetadataItem] = values.map(new BasicString(_))
    emd.getTerm _ expects licenseTerm returning items.asJava
  }

  private val accessRightsTerm = new Term(Name.ACCESSRIGHTS, Namespace.DCTERMS)

  private def expectEmdRights(accessCategory: AccessCategory) = {
    emd.getEmdRights _ expects() returning rights
    rights.getAccessCategory _ expects() returning accessCategory
  }

  private def expectRightsTerms(accessCategory: AccessCategory) = {
    val items: Seq[MetadataItem] = Seq(new BasicString(accessCategory.toString))
    emd.getTerm _ expects accessRightsTerm returning items.asJava
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
      FileItem("ABC", FileAccessRight.ANONYMOUS, Some("123")),
      FileItem("DEF", FileAccessRight.KNOWN, Some("none")),
      FileItem("GHI", FileAccessRight.RESTRICTED_GROUP, Some(""))
    )

    testInstance.filesTable(input).asScala should contain theSameElementsAs List(
      Map(FilePath.keyword -> "ABC", FileChecksum.keyword -> "123", FileAccessibleTo.keyword -> "Anonymous").asJava,
      Map(FilePath.keyword -> "DEF", FileChecksum.keyword -> checkSumNotCalculated, FileAccessibleTo.keyword -> "Known").asJava,
      Map(FilePath.keyword -> "GHI", FileChecksum.keyword -> checkSumNotCalculated, FileAccessibleTo.keyword -> "Restricted group").asJava
    )
  }
}
