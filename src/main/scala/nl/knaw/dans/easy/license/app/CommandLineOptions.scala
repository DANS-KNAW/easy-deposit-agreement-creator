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
package nl.knaw.dans.easy.license.app

import java.io.File
import javax.naming.Context
import javax.naming.ldap.InitialLdapContext

import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import nl.knaw.dans.easy.license.DatasetID
import nl.knaw.dans.easy.license.internal.{Parameters, Version}
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.apache.commons.configuration.PropertiesConfiguration
import org.rogach.scallop._

class CommandLineOptions(args: Array[String]) extends ScallopConf(args) {
  
  appendDefaultToDescription = true
  editBuilder(_.setHelpWidth(110))

  printedName = "easy-license-creator"

  version(s"$printedName v${Version()}")
  banner(s"""
           |Create a license for the given datasetID. The license will be saved at the indicated location.
           |
           |Usage:
           |
           |$printedName [{--sample|-s}] <datasetID> <license-file>
           |
           |Options:
           |""".stripMargin)

  val datasetID: ScallopOption[DatasetID] = trailArg(name = "dataset-id",
    descr = "The ID of the dataset of which a license has to be created")

  val outputFile: ScallopOption[File] = trailArg(name = "license-file",
    descr = "The file location where the license needs to be stored")

  val isSample: ScallopOption[Boolean] = opt(name = "sample", short = 's', default = Option(false),
    descr = "Indicates whether or not a sample license needs to be created")

  verify()
}

object CommandLineOptions extends DebugEnhancedLogging {

  def parse(args: Array[String], props: PropertiesConfiguration): (Parameters, File) = {
    logger.debug("Parsing command line ...")
    val opts = new CommandLineOptions(args)

    val params = new Parameters(
      templateResourceDir = new File(props.getString("license.resources")),
      datasetID = opts.datasetID(),
      isSample = opts.isSample(),
      fedoraClient = new FedoraClient(new FedoraCredentials(
        props.getString("fcrepo.url"),
        props.getString("fcrepo.user"),
        props.getString("fcrepo.password"))),
      ldapContext = {
        import java.{util => ju}

        val env = new ju.Hashtable[String, String]
        env.put(Context.PROVIDER_URL, props.getString("auth.ldap.url"))
        env.put(Context.SECURITY_AUTHENTICATION, "simple")
        env.put(Context.SECURITY_PRINCIPAL, props.getString("auth.ldap.user"))
        env.put(Context.SECURITY_CREDENTIALS, props.getString("auth.ldap.password"))
        env.put(Context.INITIAL_CONTEXT_FACTORY, "com.sun.jndi.ldap.LdapCtxFactory")

        new InitialLdapContext(env, null)
      })
    val outputFile = opts.outputFile()

    logger.debug(s"Using the following settings: $params")
    logger.debug(s"Output will be written to ${outputFile.getAbsolutePath}")

    (params, outputFile)
  }
}
