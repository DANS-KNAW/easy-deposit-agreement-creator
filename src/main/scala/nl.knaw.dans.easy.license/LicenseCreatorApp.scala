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
import javax.naming.ldap.InitialLdapContext

import com.yourmediashelf.fedora.client.{ FedoraClient, FedoraCredentials }
import rx.schedulers.Schedulers

class LicenseCreatorApp(configuration: Configuration) extends AutoCloseable {
  val templateResourceDir = new File(configuration.properties.getString("license.resources"))
  val fedoraClient = new FedoraClient(new FedoraCredentials(
    configuration.properties.getString("fcrepo.url"),
    configuration.properties.getString("fcrepo.user"),
    configuration.properties.getString("fcrepo.password")))
  val fsrbd: (String, String, String) = (
    configuration.properties.getString("fsrdb.db-connection-url"),
    configuration.properties.getString("fsrdb.db-connection-username"),
    configuration.properties.getString("fsrdb.db-connection-password"))
  val ldapContext: InitialLdapContext = {
    import java.{ util => ju }

    val env = new ju.Hashtable[String, String]
    env.put(Context.PROVIDER_URL, configuration.properties.getString("auth.ldap.url"))
    env.put(Context.SECURITY_AUTHENTICATION, "simple")
    env.put(Context.SECURITY_PRINCIPAL, configuration.properties.getString("auth.ldap.user"))
    env.put(Context.SECURITY_CREDENTIALS, configuration.properties.getString("auth.ldap.password"))
    env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")

    new InitialLdapContext(env, null)
  }

  override def close(): Unit = {
    ldapContext.close()
    Schedulers.shutdown()
  }
}
