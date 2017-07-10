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
package nl.knaw.dans.easy.license.app

import java.io.FileOutputStream

import nl.knaw.dans.easy.license.LicenseCreator
import nl.knaw.dans.easy.license.app.{CommandLineOptions => cmd}
import nl.knaw.dans.easy.license.internal._
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import rx.schedulers.Schedulers

import scala.language.postfixOps

object Command extends ApplicationSettings with DebugEnhancedLogging {

  def main(args: Array[String]): Unit = {
    logger.debug("Starting command line interface")

    try {
      implicit val (parameters, outputFile) = cmd.parse(args, props)

      new FileOutputStream(outputFile)
        .usedIn(LicenseCreator(parameters).createLicense)
        .doOnCompleted(logger.info(s"license saved at ${outputFile.getAbsolutePath}"))
        .doOnTerminate {
          // close LDAP at the end of the main
          logger.debug("closing ldap")
          parameters.ldap.close()
        }
        .toBlocking
        .subscribe(
          _ => {},
          e => logger.error("An error was caught in main:", e),
          () => logger.debug("completed"))

      Schedulers.shutdown()
    }
    catch {
      case e: Throwable => logger.error("An error was caught in main:", e)
    }
  }
}
