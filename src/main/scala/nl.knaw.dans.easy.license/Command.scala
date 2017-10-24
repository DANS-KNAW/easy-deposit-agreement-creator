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

import java.io.FileOutputStream

import nl.knaw.dans.lib.error._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import resource._

import scala.language.reflectiveCalls
import scala.util.control.NonFatal
import scala.util.{ Failure, Success, Try }

object Command extends App with DebugEnhancedLogging {
  type FeedBackMessage = String

  val configuration = Configuration()
  val commandLine: CommandLineOptions = new CommandLineOptions(args, configuration) {
    verify()
  }
  val app = new LicenseCreatorApp(configuration)

  managed(app)
    .acquireAndGet(runSubcommand)
    .doIfSuccess(msg => println(s"OK: $msg"))
    .doIfFailure { case e => logger.error(e.getMessage, e) }
    .doIfFailure { case NonFatal(e) => println(s"FAILED: ${ e.getMessage }") }


  private def runSubcommand(app: LicenseCreatorApp): Try[FeedBackMessage] = {
    commandLine.subcommand
      .collect {
        case commandLine.runService => runAsService(app)
        case _ => Failure(new IllegalArgumentException(s"Unknown command: ${ commandLine.subcommand }"))
      }
      .getOrElse {
        val params = new internal.Parameters(
          templateResourceDir = app.templateResourceDir,
          datasetID = commandLine.datasetID(),
          isSample = commandLine.isSample(),
          fedoraClient = app.fedoraClient,
          ldapContext = app.ldapContext,
          fsrdb = app.fsrbd,
          fileLimit = app.fileLimit)
        val outputFile = commandLine.outputFile()

        logger.debug(s"Using the following settings: $params")
        logger.debug(s"Output will be written to ${ outputFile.getAbsolutePath }")
        var success: Boolean = false
        new FileOutputStream(outputFile)
          .usedIn(LicenseCreator(params).createLicense)
          .doOnCompleted(logger.info(s"license saved at ${ outputFile.getAbsolutePath }"))
          .doOnCompleted { success = true }
          .toBlocking
          .subscribe(
            _ => {},
            e => logger.error("An error was caught in main:", e),
            () => logger.debug("completed"))
        if (success) Success(s"Created license for ${ commandLine.datasetID() }")
        else Failure(new Exception(s"Could not create license for ${ commandLine.datasetID() }"))
      }
  }

  private def runAsService(app: LicenseCreatorApp): Try[FeedBackMessage] = Try {
    val service = new LicenseCreatorService(configuration.properties.getInt("daemon.http.port"), app)
    Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
      override def run(): Unit = {
        service.stop()
        service.destroy()
      }
    })

    service.start()
    Thread.currentThread.join()
    "Service terminated normally."
  }
}
