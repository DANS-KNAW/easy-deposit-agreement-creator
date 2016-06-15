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

import java.io.{InputStream, OutputStream}

import org.slf4j.LoggerFactory

import scala.sys.process._

trait PdfGenerator {

  def createPdf(input: InputStream, output: OutputStream): ProcessBuilder
}

class WeasyPrintPdfGenerator(implicit parameters: BaseParameters) extends PdfGenerator {

  val log = LoggerFactory.getLogger(getClass)

  def createPdf(input: InputStream, output: OutputStream): ProcessBuilder = {
    log.debug("create pdf")

    parameters.pdfScript.getAbsolutePath #< input #> output
  }
}
