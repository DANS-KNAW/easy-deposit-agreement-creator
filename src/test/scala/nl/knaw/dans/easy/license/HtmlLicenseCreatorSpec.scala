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
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd._
import nl.knaw.dans.pf.language.emd.types.IsoDate
import org.joda.time.DateTime
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

class HtmlLicenseCreatorSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

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

  implicit val parameters = new Parameters(null, new File(testDir, "template"), null, null, null, null, null)

  before {
    new File(getClass.getResource("/velocity/").toURI)
      .copyDir(parameters.templateDir)
  }

  after {
    parameters.templateDir.deleteDirectory()
  }

  override def afterAll = testDir.getParentFile.deleteDirectory()

  "header" should "yield a map of the DOI, date and title" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () returning ident twice()
    ident.getDansManagedDoi _ expects () returning "12.3456/dans-ab7-cdef" twice()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning dates
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).header(emd)

    res should contain (DansManagedDoi, "12.3456/dans-ab7-cdef")
    res should contain (DansManagedEncodedDoi, "12.3456%2Fdans-ab7-cdef")
    res should contain (DateSubmitted, "1992-07-30")
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  it should "yield a map with default values if the actual values are null" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdIdentifier _ expects () returning ident twice()
    ident.getDansManagedDoi _ expects () returning null twice()
    emd.getEmdDate _ expects () returning date
    date.getEasDateSubmitted _ expects () returning ju.Collections.emptyList()
    emd.getPreferredTitle _ expects () returning "my preferred title"

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).header(emd)

    res should contain (DansManagedDoi, "")
    res should contain (DansManagedEncodedDoi, "")
    res should contain (DateSubmitted, new IsoDate().toString)
    res should contain (Title, "my preferred title")

    res should have size 4
  }

  "depositor" should "yield a map with depositor data" in {
    val depositor = new EasyUser("uid", "name", "org", "addr", "postal", "city", "country", "tel", "mail")

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).depositor(depositor)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).accessRights(emd)

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

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).embargo(emd)

    res should contain (UnderEmbargo, true)
    res should contain (DateAvailable, nextYear.toString("YYYY-MM-dd"))

    res should have size 2
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when there is no embargo" in {
    val dates = ju.Arrays.asList(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))

    emd.getEmdDate _ expects () returning date
    date.getEasAvailable _ expects () returning dates

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).embargo(emd)

    res should contain (UnderEmbargo, false)
    res should contain (DateAvailable, "1992-07-30")

    res should have size 2
  }

  it should "give the embargo keyword mappings with UnderEmbargo=false when no DateAvailable is available" in {
    emd.getEmdDate _ expects () returning date
    date.getEasAvailable _ expects () returning ju.Collections.emptyList()

    val res = new HtmlLicenseCreator(new File(parameters.templateDir, "MetadataTestTerms.properties")).embargo(emd)

    res should contain (UnderEmbargo, false)
    res should contain (DateAvailable, "")

    res should have size 2
  }
}
