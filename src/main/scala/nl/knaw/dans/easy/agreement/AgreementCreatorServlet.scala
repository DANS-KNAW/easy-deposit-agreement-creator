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

import java.io.ByteArrayOutputStream

import com.yourmediashelf.fedora.client.FedoraClientException
import nl.knaw.dans.easy.agreement.internal.{ Parameters => Params }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.logging.servlet._
import org.scalatra._

import scala.util.{ Failure, Success, Try }

class AgreementCreatorServlet(app: AgreementCreatorApp) extends ScalatraServlet
  with ServletLogger
  with PlainLogFormatter
  with DebugEnhancedLogging {

  get("/") {
    Ok(s"Agreement Creator Service running v${ app.version }.").logResponse
  }

  post("/create") {
    contentType = "application/pdf"
    val output = new ByteArrayOutputStream()
    (createParameters()
      .recoverWith { case nse: NoSuchElementException => Failure(new IllegalArgumentException(s"mandatory parameter was not provided: ${ nse.getMessage }")) }
      .flatMap(validateDatasetIdExistsInFedora)
      .flatMap(createAgreement(_, output)) match {
      case Success(_) => Ok(output.toByteArray)
      case Failure(fce: FedoraClientException) if fce.getMessage.contains("404") => NotFound(fce.getMessage)
      case Failure(nse: NoSuchElementException) => NotFound(nse.getMessage)
      case Failure(iae: IllegalArgumentException) => BadRequest(iae.getMessage)
      case Failure(t: Throwable) => InternalServerError(t.getMessage)
    }).logResponse
  }

  private def validateDatasetIdExistsInFedora(pars: Params): Try[Params] = Try {
    val datasetIDExists = pars.fedora.datasetIdExists(pars.datasetID)
    if (!datasetIDExists) throw new NoSuchElementException(s"Dataset ID ${ pars.datasetID } was not found in fedora")
    else pars
  }

  private def createParameters(): Try[Params] = Try {
    new Params(
      templateResourceDir = app.templateResourceDir,
      datasetID = params("datasetId"),
      isSample = params.get("sample").fold(false)(_.toBoolean),
      fedoraClient = app.fedoraClient,
      ldapEnv = app.ldapEnv,
      fsrdb = app.fsrdb,
      fileLimit = app.fileLimit,
    )
  }

  private def createAgreement(parameters: Params, output: ByteArrayOutputStream): Try[Unit] = Try {
    output
      .usedIn(AgreementCreator(parameters).createAgreement)
      .doOnTerminate(parameters.close())
      .toBlocking
      .subscribe(_ => {},
        e => throw e,
        () => debug("completed"))
  }
}
