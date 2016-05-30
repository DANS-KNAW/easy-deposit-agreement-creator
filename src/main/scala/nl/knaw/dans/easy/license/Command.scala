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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, FileOutputStream, OutputStream}

import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.easy.license.{CommandLineOptions => cmd}
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable
import rx.schedulers.Schedulers

import scala.language.postfixOps

object Command {
  val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    log.debug("Starting command line interface")

    try {
      implicit val parameters = cmd.parse(args)

      new FileOutputStream(parameters.outputFile)
        .usedIn(run)
        .doOnTerminate {
          // close LDAP at the end of the main
          log.debug("closing ldap")
          parameters.ldap.close()
        }
        .subscribe(_ => {}, e => log.error("An error was caught in main:", e), () => log.debug("completed"))

      Schedulers.shutdown()
    }
    catch {
      case e: Throwable => log.error("An error was caught in main:", e)
    }
  }

  /**
    * Create a license agreement and write it the `outputStream`. The `parameters` object a.o. contain
    * the datasetID and the depositorID (optional).
    *
    * ``Notice:`` neither the `outputStream` nor the `LdapContext` in `parameters` are closed at the
    * end of this operation.
    *
    * @param outputStream The stream to which the license agreement needs to be written
    * @param parameters The runtime parameters for this operation.
    * @return `Observable[Nothing]`: the output is written to `outputStream`; if an error occurs, it is
    *         received via the `Observable`.
    */
  def run(outputStream: OutputStream)(implicit parameters: Parameters): Observable[Nothing] = {
    implicit val ldap = parameters.ldap
    implicit val fedora = new FedoraClient(parameters.fedora)

    Dataset.getDatasetByID(parameters.datasetID)
      .flatMap(run(_, outputStream))
  }

  def run(dataset: Dataset, outputStream: OutputStream)(implicit parameters: Parameters, client: FedoraClient): Observable[Nothing] = {
    new ByteArrayOutputStream().usedIn(templateOut => {
      val htmlLicenseCreator = new HtmlLicenseCreator(metadataTermsProperties)
      val velocityTemplateResolver = new VelocityTemplateResolver(velocityProperties)

      htmlLicenseCreator.datasetToPlaceholderMap(dataset)
        .flatMap(velocityTemplateResolver.createTemplate(templateOut, _)
          .flatMap(_ => new ByteArrayInputStream(templateOut.toByteArray)
            .use(templateIn => PdfLicenseCreator.createPdf(templateIn, outputStream).!))
          .toObservable)
        .filter(_ => false)
        .asInstanceOf[Observable[Nothing]]
    })
  }
}
