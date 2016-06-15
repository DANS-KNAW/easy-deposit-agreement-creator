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
object LicenseCreator {

  def apply(implicit parameters: BaseParameters) = {
    new LicenseCreator(
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }
}

abstract class AbstractLicenseCreator(placeholderMapper: PlaceholderMapper,
                                      templateResolver: TemplateResolver,
                                      pdfGenerator: PdfGenerator)
                                     (implicit parameters: BaseParameters)
  extends LicenseCreator(placeholderMapper, templateResolver, pdfGenerator)(parameters) {

  def getDataset: Observable[Dataset]

  def createLicense(outputStream: OutputStream): Observable[Nothing] = {
    getDataset.flatMap(createLicense(_)(outputStream).toObservable)
      .filter(_ => false) // discard all elements
      .asInstanceOf[Observable[Nothing]]
  }
}

class CommandLineLicenseCreator(datasetLoader: DatasetLoader,
                                placeholderMapper: PlaceholderMapper,
                                templateResolver: TemplateResolver,
                                pdfGenerator: PdfGenerator)
                               (implicit parameters: Parameters)
  extends AbstractLicenseCreator(placeholderMapper, templateResolver, pdfGenerator)(parameters) {

  def getDataset: Observable[Dataset] = datasetLoader.getDatasetById(parameters.datasetID)
}
object CommandLineLicenseCreator {

  def apply(implicit parameters: Parameters) = {
    new CommandLineLicenseCreator(
      new DatasetLoaderImpl,
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }
}

class StageDatasetLicenseCreator(emd: EasyMetadata, depositorID: DepositorID, files: Seq[FileItem])
                                (datasetLoader: DatasetLoader,
                                 placeholderMapper: PlaceholderMapper,
                                 templateResolver: TemplateResolver,
                                 pdfGenerator: PdfGenerator)
                                (implicit parameters: Parameters)
  extends AbstractLicenseCreator(placeholderMapper, templateResolver, pdfGenerator)(parameters) {

  def getDataset: Observable[Dataset] = datasetLoader.getDataset(parameters.datasetID, emd, depositorID, files)
}
object StageDatasetLicenseCreator {

  def apply(emd: EasyMetadata, depositorID: DepositorID, files: Seq[FileItem])(implicit parameters: Parameters) = {
    new CommandLineLicenseCreator(
      new DatasetLoaderImpl,
      new PlaceholderMapper(metadataTermsProperties),
      new VelocityTemplateResolver(velocityProperties),
      new WeasyPrintPdfGenerator
    )
  }
}
