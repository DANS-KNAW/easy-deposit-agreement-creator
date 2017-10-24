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
package nl.knaw.dans.easy.license

import java.io.ByteArrayOutputStream

import nl.knaw.dans.easy.license.internal.{ Parameters => Params }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.scalatra._

class LicenseCreatorServlet(app: LicenseCreatorApp) extends ScalatraServlet with DebugEnhancedLogging {
  get("/") {
    Ok("License Creator Service running")
  }

  post("/create") {
    contentType = "application/pdf"

    val parameters = new Params(
      templateResourceDir = app.templateResourceDir,
      datasetID = params("datasetId"),
      isSample = false,
      fedoraClient = app.fedoraClient,
      ldapContext = app.ldapContext,
      fsrdb = app.fsrbd,
      fileLimit = app.fileLimit)

    var success = false // TODO: get rid of var
    val output = new ByteArrayOutputStream()
    output
      .usedIn(LicenseCreator(parameters).createLicense)
      .doOnCompleted { success = true }
      .toBlocking
      .subscribe(
        _ => {},
        e => logger.error("An error was caught in main:", e),
        () => debug("completed"))

    if(success) Ok(output.toByteArray)
    else InternalServerError() // TODO: distinguish between server errors and client errors
  }
}
