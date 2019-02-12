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
package nl.knaw.dans.easy.license

import java.io.File
import javax.naming.Context

import com.yourmediashelf.fedora.client.{ FedoraClient, FedoraCredentials }
import rx.schedulers.Schedulers

class LicenseCreatorApp(configuration: Configuration) extends AutoCloseable {
  val templateResourceDir = new File(configuration.properties.getString("license.resources"))
  val fedoraClient = new FedoraClient(new FedoraCredentials(
    configuration.properties.getString("fcrepo.url"),
    configuration.properties.getString("fcrepo.user"),
    configuration.properties.getString("fcrepo.password")))
  val fsrdb: (String, String, String) = (
    configuration.properties.getString("fsrdb.db-connection-url"),
    configuration.properties.getString("fsrdb.db-connection-username"),
    configuration.properties.getString("fsrdb.db-connection-password"))
  val fileLimit: Int = configuration.properties.getInt("license.fileLimit")
  val ldapEnv: LdapEnv = new LdapEnv {
    put(Context.PROVIDER_URL, configuration.properties.getString("auth.ldap.url"))
    put(Context.SECURITY_AUTHENTICATION, "simple")
    put(Context.SECURITY_PRINCIPAL, configuration.properties.getString("auth.ldap.user"))
    put(Context.SECURITY_CREDENTIALS, configuration.properties.getString("auth.ldap.password"))
    put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")
  }

  override def close(): Unit = {
    Schedulers.shutdown()
  }
}
