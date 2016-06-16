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

import nl.knaw.dans.pf.language.emd.EasyMetadata
import org.slf4j.LoggerFactory
import rx.lang.scala.Observable

import scala.util.Try

class LicenseCreator(placeholderMapper: PlaceholderMapper,
                     templateResolver: TemplateResolver,
                     pdfGenerator: PdfGenerator)
                    (implicit parameters: BaseParameters) {

  val log = LoggerFactory.getLogger(getClass)

  def createLicense(dataset: Dataset)(outputStream: OutputStream): Try[Unit] = {
    new ByteArrayOutputStream()
      .use(templateOut => {
        log.info(s"""creating the license for dataset "${dataset.datasetID}"""")
        for {
          placeholders <- placeholderMapper.datasetToPlaceholderMap(dataset)
          _ <- templateResolver.createTemplate(templateOut, placeholders)
          pdfInput = new ByteArrayInputStream(templateOut.toByteArray)
          _ <- pdfInput.use(pdfGenerator.createPdf(_, outputStream).!)
        } yield ()
      })
      .flatten
  }
}

class LicenseCreatorWithDatasetLoader(datasetLoader: DatasetLoader,
                                      placeholderMapper: PlaceholderMapper,
                                      templateResolver: TemplateResolver,
                                      pdfGenerator: PdfGenerator)
                                     (implicit parameters: Parameters)
  extends LicenseCreator(placeholderMapper, templateResolver, pdfGenerator)(parameters) {

  // used for command line application
  def createLicense(outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDatasetById(parameters.datasetID)
      .flatMap(createLicense(_)(outputStream).toObservable)
      .filter(_ => false)
      .asInstanceOf[Observable[Nothing]]
  }

  // used in Easy-Ingest-Flow
  def createLicense(emd: EasyMetadata, depositorID: DepositorID, files: Seq[FileItem])
                   (outputStream: OutputStream): Observable[Nothing] = {
    datasetLoader.getDataset(parameters.datasetID, emd, depositorID, files)
      .flatMap(createLicense(_)(outputStream).toObservable)
      .filter(_ => false)
      .asInstanceOf[Observable[Nothing]]
  }
}

object LicenseCreator {

  def apply(implicit parameters: BaseParameters) = {
    new LicenseCreator(
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }

  def apply(implicit parameters: Parameters) = {
    new LicenseCreatorWithDatasetLoader(
      new DatasetLoaderImpl,
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }
}
