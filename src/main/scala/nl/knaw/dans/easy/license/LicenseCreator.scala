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

import java.io.{ByteArrayInputStream, ByteArrayOutputStream, OutputStream}
import java.util.Properties

import nl.knaw.dans.pf.language.emd.EasyMetadata
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.util.Try

class LicenseCreator(datasetLoader: DatasetLoader,
                     placeholderMapper: PlaceholderMapper,
                     templateResolver: TemplateResolver,
                     pdfGenerator: PdfGenerator)(implicit parameters: Parameters) {

  val log = LoggerFactory.getLogger(getClass)

  /**
    * Create a license agreement and write it the `outputStream`. The `parameters` object a.o. contain
    * the datasetID.
    *
    * ``Notice:`` neither the `outputStream` nor the `LdapContext` in `parameters` are closed at the
    * end of this operation.
    *
    * @param outputStream The stream to which the license agreement needs to be written
    * @return `Observable[Nothing]`: the output is written to `outputStream`; if an error occurs, it is
    *         received via the `Observable`.
    */
  // used by modification tools; only the datasetID is known
  def createLicense(outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDatasetById(parameters.datasetID)
      .run(outputStream)
  }

  // used by business layer; datasetID, emd and depositor data are known, audience titles and file details require querying from Fedora
  def createLicense(emd: EasyMetadata, easyUser: EasyUser)(outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDataset(parameters.datasetID, emd, easyUser)
      .run(outputStream)
  }

  def createLicenseForBusinessLayer(emd: EasyMetadata, easyUser: EasyUser, outputStream: OutputStream): Unit = {
    createLicense(emd, easyUser)(outputStream).toBlocking.toList
  }

  // used by Stage-Dataset; emd, depositorID and file details are known, audience titles and depositor data require querying from Fedora and LDAP
  def createLicense(emd: EasyMetadata, depositorID: DepositorID, files: Seq[FileItem])(outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDataset(parameters.datasetID, emd, depositorID, files)
      .run(outputStream)
  }

  implicit class Run(dataset: Observable[Dataset]) {
    def run(outputStream: OutputStream): Observable[Nothing] = {
      dataset.flatMap(createLicense(_)(outputStream).toObservable)
        .filter(_ => false) // discard all elements
        .asInstanceOf[Observable[Nothing]]
    }
  }

  def createLicense(dataset: Dataset)(outputStream: OutputStream): Try[Int] = {
    new ByteArrayOutputStream()
      .use(templateOut => {
        log.info(s"""creating the license for dataset "${dataset.datasetID}"""")
        for {
          placeholders <- placeholderMapper.datasetToPlaceholderMap(dataset)
          _ <- templateResolver.createTemplate(templateOut, placeholders)
          pdfInput = new ByteArrayInputStream(templateOut.toByteArray)
          result <- pdfInput.use(pdfGenerator.createPdf(_, outputStream).!)
        } yield result
      })
      .flatten
  }
}

object LicenseCreator {

  val log = LoggerFactory.getLogger(getClass)

  def apply(implicit parameters: Parameters) = {
    val velocityProps = velocityProperties.fold(s => { log.error(s); new Properties() }, identity)

    new LicenseCreator(
      new DatasetLoaderImpl,
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProps),
      new WeasyPrintPdfGenerator
    )
  }
}
