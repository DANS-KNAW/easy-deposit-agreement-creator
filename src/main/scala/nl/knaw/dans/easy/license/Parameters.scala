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
import nl.knaw.dans.easy.license.internal.{BaseParameters, Parameters}

object BaseParameters {

  def apply(templateResourceDir: File, datasetID: DatasetID, isSample: Boolean): BaseParameters = {
    new internal.BaseParameters(templateResourceDir, datasetID, isSample)
  }
}

object Parameters {

  def apply(templateResourceDir: File,
            datasetID: DatasetID,
            isSample: Boolean,
            fedoraClient: FedoraClient,
            ldapContext: LdapContext): Parameters = {
    new internal.Parameters(templateResourceDir, datasetID, isSample, fedoraClient, ldapContext)
  }
}
