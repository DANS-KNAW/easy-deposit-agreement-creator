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

import better.files.File
import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import resource.managed

import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }
import scala.language.reflectiveCalls

object Command extends App with DebugEnhancedLogging {
  type FeedBackMessage = String

  val configuration = Configuration(File(System.getProperty("app.home")))
  val commandLine: CommandLineOptions = new CommandLineOptions(args, configuration) {
    verify()
  }
  val app = new EasyDepositAgreementCreatorApp(configuration)

  runSubcommand(app)
    .doIfSuccess(msg => Console.err.println(s"OK: $msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => Console.err.println(s"FAILED: ${ e.getMessage }") }

  private def runSubcommand(app: EasyDepositAgreementCreatorApp): Try[FeedBackMessage] = {
    commandLine.subcommand
      .collect {
        case commandLine.runService =>
          runAsService(app)
        case generate @ commandLine.generate =>
          runGenerateCommand(generate.datasetId(), generate.isSample(), generate.outputFile.toOption)(app)
        case _ =>
          Failure(new IllegalArgumentException(s"Unknown command: ${ commandLine.subcommand }"))
      }
      .getOrElse(Success(s"Missing subcommand. Please refer to '${ commandLine.printedName } --help'."))
  }

  private def runGenerateCommand(datasetId: DatasetId, isSample: Boolean, outputFile: Option[File])
                                (app: EasyDepositAgreementCreatorApp): Try[FeedBackMessage] = {
    outputFile
      .map(file => managed(file.createFileIfNotExists().newOutputStream))
      .getOrElse(managed(Console.out))
      .map(os => for {
        _ <- app.validateDatasetIdExistsInFedora(datasetId)
        _ <- app.createAgreement(datasetId, isSample)(() => os)
      } yield s"agreement for dataset $datasetId was created successfully")
      .tried
      .flatten
      .recoverWith { case e => Failure(new Exception(s"Could not create agreement for $datasetId: ${ e.getMessage }")) }
  }

  private def runAsService(app: EasyDepositAgreementCreatorApp): Try[FeedBackMessage] = Try {
    val service = new EasyDepositAgreementCreatorService(configuration.serverPort, Map(
      "/" -> new EasyDepositAgreementCreatorServlet(app, configuration.version),
    ))
    Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
      override def run(): Unit = {
        service.stop().unsafeGetOrThrow
        service.destroy().unsafeGetOrThrow
      }
    })

    service.start().unsafeGetOrThrow
    Thread.currentThread.join()
    "Service terminated normally."
  }
}
