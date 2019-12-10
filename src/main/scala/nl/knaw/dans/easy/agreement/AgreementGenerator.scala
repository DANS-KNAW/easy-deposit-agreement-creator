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
package nl.knaw.dans.easy.agreement

import java.net.URL

import nl.knaw.dans.easy.agreement.datafetch.{ Dataset, EasyUser }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.io.IOUtils
import org.eclipse.jetty.http.HttpStatus.OK_200
import org.json4s.JsonDSL._
import org.json4s.native.Serialization
import org.json4s.{ DefaultFormats, Formats, JValue }
import scalaj.http.{ BaseHttp, HttpResponse }

import scala.io.Source
import scala.util.{ Failure, Success, Try }

class AgreementGenerator(http: BaseHttp, url: URL) extends DebugEnhancedLogging {

  private implicit val jsonFormats: Formats = DefaultFormats

  def generate(dataset: Dataset, isSample: Boolean)(outputStreamProvider: OutputStreamProvider): Try[Unit] = {
    val json = datasetToJSON(dataset, isSample)
    val jsonString = Serialization.write(json)
    debug(s"calling easy-deposit-agreement-generator with body: $jsonString")
    val response = http(url.toString).postData(jsonString).header("content-type", "application/json").exec {
      case (OK_200, _, is) => IOUtils.copyLarge(is, outputStreamProvider())
      case (_, _, is) => Source.fromInputStream(is).mkString
    }
    if (response.code == OK_200) Success(())
    else Failure(GeneratorError(s"Could not generate agreement for dataset ${ dataset.datasetId }", HttpResponse(response.body.asInstanceOf[String], response.code, response.headers)))
  }

  private def datasetToJSON(dataset: Dataset, isSample: Boolean): JValue = {
    ("depositor" -> depositorToJSON(dataset.easyUser)) ~
      ("doi" -> dataset.doi) ~
      ("title" -> dataset.title) ~
      ("dateSubmitted" -> dataset.dateSubmitted) ~
      ("dateAvailable" -> dataset.dateAvailable) ~
      ("accessCategory" -> dataset.accessCategory) ~
      ("license" -> dataset.license) ~
      ("sample" -> isSample) ~
      ("agreementVersion" -> "4.0") ~
      ("agreementLanguage" -> "EN")
  }

  private def depositorToJSON(user: EasyUser): JValue = {
    ("name" -> user.name) ~
      ("address" -> user.address) ~
      ("zipcode" -> user.postalCode) ~
      ("city" -> user.city) ~
      ("country" -> user.country) ~
      ("organisation" -> user.organization) ~
      ("phone" -> user.telephone) ~
      ("email" -> user.email)
  }
}
