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

import java.io.File
import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.FedoraClient

// this class needs to be in a separate file rather than in package.scala because of interop with
// java business layer.
class BaseParameters(val templateResourceDir: File,
                     val datasetID: DatasetID,
                     val isSample: Boolean)

trait DatabaseParameters {
  val fedora: Fedora
  val ldap: Ldap
}

case class Parameters(override val templateResourceDir: File,
                      override val datasetID: DatasetID,
                      override val isSample: Boolean,
                      private val fedoraClient: FedoraClient,
                      private val ldapContext: LdapContext)
  extends BaseParameters(templateResourceDir, datasetID, isSample) with DatabaseParameters {

  val fedora = FedoraImpl(fedoraClient)
  val ldap = LdapImpl(ldapContext)
}
