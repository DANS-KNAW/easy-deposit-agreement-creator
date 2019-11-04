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
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.lib.logging.servlet._
import org.scalatra._

import scala.util.{ Failure, Success, Try }

class AgreementCreatorServlet(app: AgreementCreatorApp) extends ScalatraServlet
  with ServletLogger
  with PlainLogFormatter
  with LogResponseBodyOnError
  with DebugEnhancedLogging {

  get("/") {
    Ok(s"Agreement Creator Service running v${ app.version }.")
  }

  post("/create") {
    contentType = "application/pdf"
    val output = new ByteArrayOutputStream()

    val result = for {
      params <- createParameters().recoverWith {
        case nse: NoSuchElementException => Failure(new IllegalArgumentException(s"mandatory parameter was not provided: ${ nse.getMessage }"))
      }
      _ <- validateDatasetIdExistsInFedora(params)
      actionResult <- createAgreement(params, output)
    } yield actionResult

    result.getOrRecover {
      case nse: NoSuchElementException => NotFound(nse.getMessage)
      case iae: IllegalArgumentException => BadRequest(iae.getMessage)
      case t => InternalServerError(t.getMessage)
    }
  }

  private def validateDatasetIdExistsInFedora(pars: Params): Try[Unit] = {
    logger.info(s"check if dataset ${ pars.datasetID } exists")
    pars.fedora.datasetIdExists(pars.datasetID).flatMap {
      case true => Success(())
      case false => Failure(new NoSuchElementException(s"DatasetId ${ pars.datasetID } does not exist"))
    }
  }

  private def createParameters(): Try[Params] = Try {
    new Params(
      templateResourceDir = app.templateResourceDir,
      datasetID = params("datasetId"),
      isSample = params.get("sample").fold(false)(_.toBoolean),
      fedoraClient = app.fedoraClient,
      ldapEnv = app.ldapEnv,
      fsrdb = app.fsrdb,
    )
  }

  private def createAgreement(parameters: Params, output: ByteArrayOutputStream): Try[ActionResult] = Try {
    output
      .usedIn(AgreementCreator(parameters).createAgreement)
      .doOnTerminate { parameters.close() }
      .doOnCompleted { debug("completed") }
      .onErrorReturn {
        case fce: FedoraClientException if fce.getMessage.contains("404") => NotFound(fce.getMessage)
        case t: Throwable => InternalServerError(t.getMessage)
      }
      .toBlocking
      .headOrElse { Ok(output.toByteArray) }
  }
}
