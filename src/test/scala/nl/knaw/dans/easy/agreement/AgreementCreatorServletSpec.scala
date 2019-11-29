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
package nl.knaw.dans.easy.agreement

import org.apache.commons.configuration.PropertiesConfiguration
import org.scalatest.FlatSpec
import org.scalatra.test.EmbeddedJettyContainer
import org.scalatra.test.scalatest.ScalatraSuite

class AgreementCreatorServletSpec extends FlatSpec
  with ScalatraSuite
  with EmbeddedJettyContainer {

  private val app = new AgreementCreatorApp(minimalAppConfig)
  private val agreementCreatorServlet = new AgreementCreatorServlet(app)
  addServlet(agreementCreatorServlet, "/*") //same as in nl.knaw.dans.easy.agreement.AgreementCreatorService

  "get /" should "respond with the service is running" in {
    get("/") {
      status shouldBe 200
      body shouldBe s"Agreement Creator Service running v${ app.version }."
    }
  }

  "post /create" should "return a bad request (400) when the param datasetId is not provided" in {
    post("/create") {
      status shouldBe 400
      body shouldBe "mandatory parameter was not provided: key not found: datasetId"
    }
  }

  def minimalAppConfig: Configuration = {
    new Configuration("1.0.0", new PropertiesConfiguration() {
      addProperty("fcrepo.url", "http://localhost:8080/fedora")
      addProperty("fcrepo.user", "-")
      addProperty("fcrepo.password", "-")
      addProperty("auth.ldap.url", "ldap://localhost")
      addProperty("auth.ldap.user", "")
      addProperty("auth.ldap.password", "")
      addProperty("agreement.resources", "")
    })
  }
}
