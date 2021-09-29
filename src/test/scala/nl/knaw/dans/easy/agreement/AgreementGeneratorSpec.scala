/*
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
package nl.knaw.dans.easy.agreement

import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets

import nl.knaw.dans.common.lang.dataset.AccessCategory
import nl.knaw.dans.easy.agreement.AgreementGenerator.PdfGenConfiguration
import nl.knaw.dans.easy.agreement.datafetch.{ Dataset, EasyUser }
import nl.knaw.dans.easy.agreement.fixture.{ FixedDateTime, TestSupportFixture }
import nl.knaw.dans.pf.language.emd.Term.Name
import nl.knaw.dans.pf.language.emd.types.{ BasicString, IsoDate }
import nl.knaw.dans.pf.language.emd._
import okhttp3.HttpUrl
import okhttp3.mockwebserver.{ MockResponse, MockWebServer }
import org.scalamock.scalatest.MockFactory
import org.scalatest.BeforeAndAfterAll
import scalaj.http.{ Http, HttpResponse }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Success }

class AgreementGeneratorSpec extends TestSupportFixture with FixedDateTime with BeforeAndAfterAll with MockFactory {

  trait MockEasyMetadata extends EasyMetadata {
    def toString(x: String, y: Name): String = ""

    def toString(x: String, y: Term): String = ""

    def toString(x: String, y: MDContainer): String = ""

    def toString(x: String): String = ""
  }

  // configure the mock server
  private val server = new MockWebServer
  private val test_server = "/generate/"
  private val baseURL: HttpUrl = server.url(test_server)

  private val emd = mock[MockEasyMetadata]
  private val identifier = mock[EmdIdentifier]
  private val date = mock[EmdDate]
  private val rights = mock[EmdRights]

  private val generator = new AgreementGenerator(Http, PdfGenConfiguration(baseURL.url(), 5000, 5000))

  override protected def afterAll(): Unit = {
    server.shutdown()
    super.afterAll()
  }

  "generate" should "generate the correct JSON and interact with easy-deposit-agreement-generator to obtain the PDF" in {
    val dates = List(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))
    val user = EasyUser(
      name = "name",
      organization = "organization",
      address = "address",
      postalCode = "postalCode",
      city = "city",
      country = "country",
      telephone = "telephone",
      email = "email",
    )
    val dataset = Dataset(
      datasetId = "easy-dataset:1",
      emd = emd,
      easyUser = user,
    )

    emd.getPreferredTitle _ expects() once() returning "my preferred title"
    emd.getEmdIdentifier _ expects() once() returning identifier
    identifier.getDansManagedDoi _ expects() once() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() twice() returning date
    date.getEasDateSubmitted _ expects() once() returning dates.asJava
    date.getEasAvailable _ expects() once() returning dates.reverse.asJava
    emd.getEmdRights _ expects() twice() returning rights
    rights.getAccessCategory _ expects() once() returning AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS
    rights.getTermsLicense _ expects() once() returning Seq("accept", "http://creativecommons.org/licenses/by-nc-sa/4.0/").map(new BasicString(_)).asJava
    server.enqueue {
      new MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .setResponseCode(200)
        .setBody("fake pdf body")
    }

    val baos = new ByteArrayOutputStream()
    generator.generate(dataset, isSample = false)(() => baos) should matchPattern { case Success(()) => }
    baos.toString shouldBe "fake pdf body"

    val request = server.takeRequest()
    request.getRequestLine shouldBe s"POST $test_server HTTP/1.1"
    request.getBody.readString(StandardCharsets.UTF_8) shouldBe """{"depositor":{"name":"name","address":"address","zipcode":"postalCode","city":"city","country":"country","organisation":"organization","phone":"telephone","email":"email"},"doi":"12.3456/dans-ab7-cdef","title":"my preferred title","dateSubmitted":"1992-07-30","dateAvailable":"2016-07-30","accessCategory":"OPEN_ACCESS_FOR_REGISTERED_USERS","license":"http://creativecommons.org/licenses/by-nc-sa/4.0/","sample":false,"agreementVersion":"4.0","agreementLanguage":"EN"}"""
  }

  it should "generate the correct JSON and interact with easy-deposit-agreement-generator with defaults from EMD" in {
    val dates = List.empty
    val user = EasyUser(
      name = "name",
      organization = "organization",
      address = "address",
      postalCode = "postalCode",
      city = "city",
      country = "country",
      telephone = "telephone",
      email = "email",
    )
    val dataset = Dataset(
      datasetId = "easy-dataset:1",
      emd = emd,
      easyUser = user,
    )

    emd.getPreferredTitle _ expects() once() returning "my preferred title"
    emd.getEmdIdentifier _ expects() once() returning identifier
    identifier.getDansManagedDoi _ expects() once() returning null
    emd.getEmdDate _ expects() twice() returning date
    date.getEasDateSubmitted _ expects() once() returning dates.asJava
    date.getEasAvailable _ expects() once() returning dates.reverse.asJava
    emd.getEmdRights _ expects() repeat 3 returning rights
    rights.getAccessCategory _ expects() twice() returning null
    rights.getTermsLicense _ expects() once() returning Seq("accept").map(new BasicString(_)).asJava
    server.enqueue {
      new MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .setResponseCode(200)
        .setBody("fake pdf body")
    }

    val baos = new ByteArrayOutputStream()
    generator.generate(dataset, isSample = false)(() => baos) should matchPattern { case Success(()) => }
    baos.toString shouldBe "fake pdf body"

    val request = server.takeRequest()
    request.getRequestLine shouldBe s"POST $test_server HTTP/1.1"
    request.getBody.readString(StandardCharsets.UTF_8) shouldBe s"""{"depositor":{"name":"name","address":"address","zipcode":"postalCode","city":"city","country":"country","organisation":"organization","phone":"telephone","email":"email"},"doi":"","title":"my preferred title","dateSubmitted":"$nowYMD","dateAvailable":"$nowYMD","accessCategory":"OPEN_ACCESS","license":"http://creativecommons.org/publicdomain/zero/1.0","sample":false,"agreementVersion":"4.0","agreementLanguage":"EN"}"""
  }

  it should "generate the correct JSON with dans-license as old license" in {
    val dates = List(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))
    val user = EasyUser(
      name = "name",
      organization = "organization",
      address = "address",
      postalCode = "postalCode",
      city = "city",
      country = "country",
      telephone = "telephone",
      email = "email",
    )
    val dataset = Dataset(
      datasetId = "easy-dataset:1",
      emd = emd,
      easyUser = user,
    )

    emd.getPreferredTitle _ expects() once() returning "my preferred title"
    emd.getEmdIdentifier _ expects() once() returning identifier
    identifier.getDansManagedDoi _ expects() once() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() twice() returning date
    date.getEasDateSubmitted _ expects() once() returning dates.asJava
    date.getEasAvailable _ expects() once() returning dates.reverse.asJava
    emd.getEmdRights _ expects() repeat 3 returning rights
    rights.getAccessCategory _ expects() twice() returning AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS
    rights.getTermsLicense _ expects() once() returning Seq("accept").map(new BasicString(_)).asJava
    server.enqueue {
      new MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .setResponseCode(200)
        .setBody("fake pdf body")
    }

    val baos = new ByteArrayOutputStream()
    generator.generate(dataset, isSample = false)(() => baos) should matchPattern { case Success(()) => }
    baos.toString shouldBe "fake pdf body"

    val request = server.takeRequest()
    request.getRequestLine shouldBe s"POST $test_server HTTP/1.1"
    request.getBody.readString(StandardCharsets.UTF_8) shouldBe """{"depositor":{"name":"name","address":"address","zipcode":"postalCode","city":"city","country":"country","organisation":"organization","phone":"telephone","email":"email"},"doi":"12.3456/dans-ab7-cdef","title":"my preferred title","dateSubmitted":"1992-07-30","dateAvailable":"2016-07-30","accessCategory":"OPEN_ACCESS_FOR_REGISTERED_USERS","license":"http://dans.knaw.nl/en/about/organisation-and-policy/legal-information/DANSLicence.pdf","sample":false,"agreementVersion":"4.0","agreementLanguage":"EN"}"""
  }

  it should "generate the correct JSON with sample=true" in {
    val dates = List(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))
    val user = EasyUser(
      name = "name",
      organization = "organization",
      address = "address",
      postalCode = "postalCode",
      city = "city",
      country = "country",
      telephone = "telephone",
      email = "email",
    )
    val dataset = Dataset(
      datasetId = "easy-dataset:1",
      emd = emd,
      easyUser = user,
    )

    emd.getPreferredTitle _ expects() once() returning "my preferred title"
    emd.getEmdIdentifier _ expects() once() returning identifier
    identifier.getDansManagedDoi _ expects() once() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() twice() returning date
    date.getEasDateSubmitted _ expects() once() returning dates.asJava
    date.getEasAvailable _ expects() once() returning dates.reverse.asJava
    emd.getEmdRights _ expects() twice() returning rights
    rights.getAccessCategory _ expects() once() returning AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS
    rights.getTermsLicense _ expects() once() returning Seq("accept", "http://creativecommons.org/licenses/by-nc-sa/4.0/").map(new BasicString(_)).asJava
    server.enqueue {
      new MockResponse()
        .addHeader("Content-Type", "application/pdf")
        .setResponseCode(200)
        .setBody("fake pdf body")
    }

    val baos = new ByteArrayOutputStream()
    generator.generate(dataset, isSample = true)(() => baos) should matchPattern { case Success(()) => }
    baos.toString shouldBe "fake pdf body"

    val request = server.takeRequest()
    request.getRequestLine shouldBe s"POST $test_server HTTP/1.1"
    request.getBody.readString(StandardCharsets.UTF_8) shouldBe """{"depositor":{"name":"name","address":"address","zipcode":"postalCode","city":"city","country":"country","organisation":"organization","phone":"telephone","email":"email"},"doi":"12.3456/dans-ab7-cdef","title":"my preferred title","dateSubmitted":"1992-07-30","dateAvailable":"2016-07-30","accessCategory":"OPEN_ACCESS_FOR_REGISTERED_USERS","license":"http://creativecommons.org/licenses/by-nc-sa/4.0/","sample":true,"agreementVersion":"4.0","agreementLanguage":"EN"}"""
  }

  it should "interact with easy-deposit-agreement-generator and process errors correctly" in {
    val dates = List(new IsoDate("1992-07-30"), new IsoDate("2016-07-30"))
    val user = EasyUser(
      name = "name",
      organization = "organization",
      address = "address",
      postalCode = "postalCode",
      city = "city",
      country = "country",
      telephone = "telephone",
      email = "email",
    )
    val dataset = Dataset(
      datasetId = "easy-dataset:1",
      emd = emd,
      easyUser = user,
    )

    emd.getPreferredTitle _ expects() once() returning "my preferred title"
    emd.getEmdIdentifier _ expects() once() returning identifier
    identifier.getDansManagedDoi _ expects() once() returning "12.3456/dans-ab7-cdef"
    emd.getEmdDate _ expects() twice() returning date
    date.getEasDateSubmitted _ expects() once() returning dates.asJava
    date.getEasAvailable _ expects() once() returning dates.reverse.asJava
    emd.getEmdRights _ expects() twice() returning rights
    rights.getAccessCategory _ expects() once() returning AccessCategory.OPEN_ACCESS_FOR_REGISTERED_USERS
    rights.getTermsLicense _ expects() once() returning Seq("accept", "http://creativecommons.org/licenses/by-nc-sa/4.0/").map(new BasicString(_)).asJava
    server.enqueue {
      new MockResponse()
        .addHeader("Content-Type", "text/plain")
        .setResponseCode(400)
        .setBody("some error message")
    }

    val baos = new ByteArrayOutputStream()
    inside(generator.generate(dataset, isSample = true)(() => baos)) {
      case Failure(GeneratorError(msg, HttpResponse(body, 400, headers))) =>
        msg shouldBe "Could not generate agreement for dataset easy-dataset:1"
        body shouldBe "some error message"
        headers should contain ("Content-Type" -> List("text/plain"))
    }

    val request = server.takeRequest()
    request.getRequestLine shouldBe s"POST $test_server HTTP/1.1"
    request.getBody.readString(StandardCharsets.UTF_8) shouldBe """{"depositor":{"name":"name","address":"address","zipcode":"postalCode","city":"city","country":"country","organisation":"organization","phone":"telephone","email":"email"},"doi":"12.3456/dans-ab7-cdef","title":"my preferred title","dateSubmitted":"1992-07-30","dateAvailable":"2016-07-30","accessCategory":"OPEN_ACCESS_FOR_REGISTERED_USERS","license":"http://creativecommons.org/licenses/by-nc-sa/4.0/","sample":true,"agreementVersion":"4.0","agreementLanguage":"EN"}"""
  }
}
