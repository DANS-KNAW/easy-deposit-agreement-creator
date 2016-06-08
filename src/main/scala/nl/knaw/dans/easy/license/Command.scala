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

import nl.knaw.dans.easy.license.{CommandLineOptions => cmd}
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable
import rx.lang.scala.schedulers.ComputationScheduler
import rx.schedulers.Schedulers

import scala.language.postfixOps

class Command(datasetLoader: DatasetLoader,
              placeholderMapper: PlaceholderMapper,
              templateResolver: TemplateResolver,
              pdfGenerator: PdfGenerator)(implicit parameters: Parameters) {

  /**
    * Create a license agreement and write it the `outputStream`. The `parameters` object a.o. contain
    * the datasetID and the depositorID (optional).
    *
    * ``Notice:`` neither the `outputStream` nor the `LdapContext` in `parameters` are closed at the
    * end of this operation.
    *
    * @param outputStream The stream to which the license agreement needs to be written
    * @return `Observable[Nothing]`: the output is written to `outputStream`; if an error occurs, it is
    *         received via the `Observable`.
    */
  def run(outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDatasetById(parameters.datasetID).flatMap(run(_, outputStream))
  }

  def run(dataset: Dataset, outputStream: OutputStream): Observable[Nothing] = {
    new ByteArrayOutputStream().usedIn(templateOut => {
      val audiences = datasetLoader.getAudiences(dataset.emd.getEmdAudience).toSeq
      val files = datasetLoader.getFilesInDataset(dataset.datasetID).toSeq

      audiences.combineLatestWith(files)(placeholderMapper.datasetToPlaceholderMap(dataset, _, _))
        .flatMap(_.toObservable)
        .observeOn(ComputationScheduler())
        .flatMap(templateResolver.createTemplate(templateOut, _)
          .flatMap(_ => new ByteArrayInputStream(templateOut.toByteArray)
              .use(templateIn => pdfGenerator.createPdf(templateIn, outputStream).!))
          .toObservable)
        .filter(_ => false) // discard all elements, we only want the onError and onCompleted
        .asInstanceOf[Observable[Nothing]]
    })
  }
}

object Command {
  val log = LoggerFactory.getLogger(getClass)

  def apply(implicit parameters: Parameters) = {
    new Command(
      new DatasetLoaderImpl,
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }

  def main(args: Array[String]): Unit = {
    log.debug("Starting command line interface")

    try {
      val parameters = cmd.parse(args)

      new FileOutputStream(parameters.outputFile)
        .usedIn(Command(parameters).run)
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
