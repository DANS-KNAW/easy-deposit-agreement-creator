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
package nl.knaw.dans.easy.agreement.internal

import java.io.File

import com.yourmediashelf.fedora.client.FedoraClient
import javax.naming.ldap.InitialLdapContext
import nl.knaw.dans.easy.agreement.{ DatasetID, LdapEnv }
import org.apache.commons.configuration.PropertiesConfiguration

import scala.collection.JavaConverters._
import scala.util.Try

// this class needs to be in a separate file rather than in package.scala because of interop with
// java business layer.
class BaseParameters(val templateResourceDir: File,
                     val datasetID: DatasetID,
                     val isSample: Boolean,
                    ) {
  private val licenseUrlPrefixRegExp = "https?://(www.)?"
  private val licencesMap: Map[String, String] = Try {
    val licenses = new PropertiesConfiguration(new File(templateResourceDir, "/template/licenses/licenses.properties"))
    licenses.getKeys.asScala.map(key =>
      key.replaceAll(licenseUrlPrefixRegExp, "") -> s"licenses/${licenses.getString(key)}"
    ).toMap
  }.getOrElse(Map.empty)

  def licenseLegalResource(url: String): String = {
    licencesMap(url.replaceAll(licenseUrlPrefixRegExp, ""))
  }
}

trait DatabaseParameters {
  val fedora: Fedora
  val ldap: Ldap
}

case class Parameters(override val templateResourceDir: File,
                      override val datasetID: DatasetID,
                      override val isSample: Boolean,
                      fedora: Fedora,
                      ldap: Ldap)
  extends BaseParameters(templateResourceDir, datasetID, isSample) with DatabaseParameters with AutoCloseable {

  def this(templateResourceDir: File, datasetID: DatasetID, isSample: Boolean, fedoraClient: FedoraClient, ldapEnv: LdapEnv) = {
    this(templateResourceDir, datasetID, isSample, FedoraImpl(fedoraClient), LdapImpl(new InitialLdapContext(ldapEnv, null)))
  }

  override def close(): Unit = {
    ldap.close()
  }

  override def toString: String = super.toString
}
