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
package nl.knaw.dans.easy.license.internal

import java.io.File
import java.sql.{ Connection, DriverManager }

import javax.naming.ldap.InitialLdapContext
import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.easy.license.{ DatasetID, LdapEnv }

// this class needs to be in a separate file rather than in package.scala because of interop with
// java business layer.
class BaseParameters(val templateResourceDir: File,
                     val datasetID: DatasetID,
                     val isSample: Boolean,
                     val fileLimit: Int)

trait DatabaseParameters {
  val fedora: Fedora
  val ldap: Ldap
  val fsrdb: Connection
  val fileLimit: Int
}

case class Parameters(override val templateResourceDir: File,
                      override val datasetID: DatasetID,
                      override val isSample: Boolean,
                      override val fileLimit: Int,
                      fedora: Fedora,
                      ldap: Ldap,
                      fsrdb: Connection)
  extends BaseParameters(templateResourceDir, datasetID, isSample, fileLimit) with DatabaseParameters {

  def this(templateResourceDir: File, datasetID: DatasetID, isSample: Boolean, fileLimit: Int, fedoraClient: FedoraClient, ldapEnv: LdapEnv, fsrdb: (String, String, String)) = {
    this(templateResourceDir, datasetID, isSample, fileLimit, FedoraImpl(fedoraClient), LdapImpl(new InitialLdapContext(ldapEnv, null)), DriverManager.getConnection(fsrdb._1, fsrdb._2, fsrdb._3))
  }

  override def toString: String = super.toString
}
