/*
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
package nl.knaw.dans.easy

import java.io.OutputStream

import scalaj.http.HttpResponse

package object agreement {

  type DatasetId = String
  type DepositorId = String
  type OutputStreamProvider = () => OutputStream
  type LdapEnv = java.util.Hashtable[String, String]

  abstract class LdapError(msg: String, cause: Option[Throwable] = Option.empty) extends Exception(msg, cause.orNull)
  case class LdapException(depositorId: DepositorId, cause: Throwable) extends LdapError(s"Error in fetching user data from LDAP for user '$depositorId'", Option(cause))
  case class NoUserFoundException(depositorId: DepositorId) extends LdapError(s"Could not find depositor with id: '$depositorId'")

  abstract class FedoraError(msg: String, cause: Option[Throwable] = Option.empty) extends Exception(msg, cause.orNull)
  case class NoDatasetFoundException(datasetId: DatasetId, cause: Throwable) extends FedoraError(s"Could not find dataset with id: $datasetId", Option(cause))
  case class FedoraUnavailableException(datasetId: DatasetId, cause: Throwable) extends FedoraError(s"Fedora was not available while looking for dataset $datasetId: ${ cause.getMessage }", Option(cause))

  case class GeneratorError(msg: String, response: HttpResponse[String]) extends Exception(s"$msg - ${ response.statusLine }, details: ${ response.body }")
}
