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

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.logging.servlet.{ LogResponseBodyOnError, PlainLogFormatter, ServletLogger }
import org.scalatra.{ InternalServerError, NotFound, Ok, ScalatraServlet }

class EasyDepositAgreementCreatorServlet(app: EasyDepositAgreementCreatorApp,
                                         version: String)
  extends ScalatraServlet
    with ServletLogger
    with PlainLogFormatter
    with LogResponseBodyOnError
    with DebugEnhancedLogging {

  get("/") {
    contentType = "text/plain"
    Ok(s"EASY Deposit Agreement Creator Service running ($version)")
  }

  post("/create") {
    contentType = "application/pdf"

    val datasetId = params("datasetId")
    val isSample = params.getAsOrElse("sample", false)
    app.createAgreement(datasetId, isSample)(() => response.outputStream)
      .map(_ => Ok())
      .doIfFailure {
        case e =>
          contentType = "text/plain"
          logger.error(e.getMessage, e)
      }
      .getOrRecover {
        case e: LdapError => NotFound(e.getMessage)
        case e: NoDatasetFoundException => NotFound(e.getMessage)
        case e: FedoraUnavailableException => InternalServerError(e.getMessage)
        case e: GeneratorError => InternalServerError(e.getMessage)
        case e => InternalServerError(e.getMessage)
      }
  }
}
