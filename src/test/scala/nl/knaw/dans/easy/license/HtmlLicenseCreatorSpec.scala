package nl.knaw.dans.easy.license

import java.io.File
import java.util
import java.util.Collections

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.IsoDate
import org.apache.velocity.exception.MethodInvocationException
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.util.{Failure, Success}

class HtmlLicenseCreatorSpec extends UnitSpec with MockFactory {

  trait ScalaEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""
    def toString(x: String, y: Term): String = ""
    def toString(x: String, y: MDContainer): String = ""
    def toString(x: String): String = ""
  }

  val emd = mock[ScalaEasyMetadata]
  val ident = mock[EmdIdentifier]
  val date = mock[EmdDate]
  val rights = mock[EmdRights]

  "header" should "yield a map of the DOI, date and title" in {
    val dates = util.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () returning ident twice()
    ident.getDansManagedDoi _ expects () returning "12.3456/dans-ab7-cdef" twice()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning dates
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = HtmlLicenseCreator.header(emd)

    res should contain (DansManagedDoi, "12.3456/dans-ab7-cdef")
    res should contain (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef")
    res should contain (DateSubmitted, "1992-07-30")
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  it should "yield a map with default values if the actual values are null" in {
    val dates = util.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () returning ident twice()
    ident.getDansManagedDoi _ expects () returning null twice()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning Collections.emptyList()
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = HtmlLicenseCreator.header(emd)

    res should contain (DansManagedDoi, "")
    res should contain (DansManagedEncodedDoi, "")
    res should contain (DateSubmitted, new IsoDate().toString)
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  "users" should "yield a map with user data" in {
    val user = new EasyUser("uid", "name", "org", "addr", "postal", "city", "country", "tel", "mail")

    val res = HtmlLicenseCreator.users(user)

    res should contain (UserName -> "name")
    res should contain (UserOrganisation -> "org")
    res should contain (UserAddress -> "addr")
    res should contain (UserPostalCode -> "postal")
    res should contain (UserCity -> "city")
    res should contain (UserCountry -> "country")
    res should contain (UserTelephone -> "tel")
    res should contain (UserEmail -> "mail")

    res should have size 8
  }

  "accessRights" should "map an Open Access category to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.OPEN_ACCESS

    val res = HtmlLicenseCreator.accessRights(emd)

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

    val res = HtmlLicenseCreator.accessRights(emd)

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

    val res = HtmlLicenseCreator.accessRights(emd)

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

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, true)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a Group Access category to an OtherAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.GROUP_ACCESS

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, true)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map a Request Permission category to an RestrictGroup keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.REQUEST_PERMISSION

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, true)
    res should contain (RestrictRequest, false)

    res should have size 5
  }

  it should "map an Access Elsewhere category to an RestrictRequest keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.ACCESS_ELSEWHERE

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, true)

    res should have size 5
  }

  it should "map a No Access category to an RestrictRequest keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.NO_ACCESS

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, false)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, true)

    res should have size 5
  }

  it should "map a null value to an OpenAccess keyword" in {
    emd.getEmdRights _ expects () returning rights
    rights.getAccessCategory _ expects () returning AccessCategory.OPEN_ACCESS

    val res = HtmlLicenseCreator.accessRights(emd)

    res should contain (OpenAccess, true)
    res should contain (OpenAccessForRegisteredUsers, false)
    res should contain (OtherAccess, false)
    res should contain (RestrictGroup, false)
    res should contain (RestrictRequest, false)

    res should have size 5
  }
}

class VelocityTemplateResolverSpec extends UnitSpec with BeforeAndAfter with BeforeAndAfterAll {

  implicit val parameters = new Parameters(null, new File(testDir, "template"), null, null, null, null, null)

  before {
    new File(getClass.getResource("/velocity/").toURI)
      .copyDir(parameters.templateDir)
  }

  after {
    parameters.templateDir.deleteDirectory()
  }

  override def afterAll = testDir.getParentFile.deleteDirectory()

  val keyword: KeywordMapping = new KeywordMapping { val keyword: String = "name" }

  "createTemplate" should """map the "name" keyword to "world" in the template and put the result in a file""" in {
    val templateCreator = new VelocityTemplateResolver(new File(parameters.templateDir, "velocity-test-engine.properties"))

    val map: Map[KeywordMapping, Object] = Map(keyword -> "world")
    val resFile = new File(testDir, "template/result.html")

    templateCreator.createTemplate(resFile, map) shouldBe a[Success[_]]

    resFile should exist
    resFile.read() should include ("<p>hello world</p>")
  }

  it should "fail if not all placeholders are filled in" in {
    val templateCreator = new VelocityTemplateResolver(new File(parameters.templateDir, "velocity-test-engine.properties"))

    val map: Map[KeywordMapping, Object] = Map.empty
    val resFile = new File(testDir, "template/result.html")

    val res = templateCreator.createTemplate(resFile, map)
    res shouldBe a[Failure[_]]
    (the [MethodInvocationException] thrownBy res.get).getMessage should include ("$name")

    resFile shouldNot exist
  }

  it should "fail when the template does not exist" in {
    try {
      new VelocityTemplateResolver(new File(parameters.templateDir, "velocity-test-engine-fail.properties"))
      fail("an error should have been thrown, but this was not the case.")
    }
    catch {
      case _: Throwable => new File(testDir, "template/result.html") shouldNot exist
    }
  }
}
