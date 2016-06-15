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

import java.io.FileOutputStream

import nl.knaw.dans.easy.license.{CommandLineOptions => cmd}
import org.slf4j.LoggerFactory
import rx.schedulers.Schedulers

import scala.language.postfixOps

object Command {
  val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    log.debug("Starting command line interface")

    try {
      implicit val (parameters, outputFile) = cmd.parse(args)

      new FileOutputStream(outputFile)
        .usedIn(CommandLineLicenseCreator(parameters).createLicense)
        .doOnCompleted(log.info(s"license saved at ${outputFile.getAbsolutePath}"))
        .doOnTerminate {
          // close LDAP at the end of the main
          log.debug("closing ldap")
          parameters.ldap.close()
        }
        .toBlocking
        .subscribe(
          _ => {},
          e => log.error("An error was caught in main:", e),
          () => log.debug("completed"))

      Schedulers.shutdown()
    }
    catch {
      case e: Throwable => log.error("An error was caught in main:", e)
    }
  }
}
