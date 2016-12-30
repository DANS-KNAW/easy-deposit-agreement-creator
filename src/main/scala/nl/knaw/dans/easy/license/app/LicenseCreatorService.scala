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

import java.io.{ByteArrayOutputStream, File, FileOutputStream}
import javax.naming.Context
import javax.naming.ldap.InitialLdapContext

import com.yourmediashelf.fedora.client.{FedoraClient, FedoraCredentials}
import nl.knaw.dans.easy.license.LicenseCreator
import nl.knaw.dans.easy.license.internal.Parameters
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import org.eclipse.jetty.ajp.Ajp13SocketConnector
import org.eclipse.jetty.server.Server
import org.scalatra.servlet.ScalatraListener
import org.eclipse.jetty.servlet.ServletContextHandler
import org.scalatra.{InternalServerError, Ok, ScalatraServlet}
import rx.schedulers.Schedulers
import nl.knaw.dans.easy.license.internal._

import scala.util.Try

class LicenseCreatorService extends ApplicationSettings with DebugEnhancedLogging {
  import logger._

  private val port = props.getInt("daemon.http.port")
  val server = new Server(port)
  val context = new ServletContextHandler(ServletContextHandler.NO_SESSIONS)
  context.addEventListener(new ScalatraListener())

  server.setHandler(context)
  info(s"HTTP port is $port")

  if (props.containsKey("daemon.ajp.port")) {
    val ajp = new Ajp13SocketConnector()
    val ajpPort = props.getInt("daemon.ajp.port")
    ajp.setPort(ajpPort)
    server.addConnector(ajp)
    info(s"AJP port is $ajpPort")
  }

  def start(): Try[Unit] = Try {
    info("Starting HTTP service ...")
    server.start()
  }

  def stop(): Try[Unit] = Try {
    info("Stopping HTTP service ...")
    server.stop()
    debug("Shutting down RX schedulers ...")
    Schedulers.shutdown()
  }

  def destroy(): Try[Unit] = Try {
    server.destroy()
  }
}

object LicenseCreatorService extends App with DebugEnhancedLogging {
  import logger._
  val service = new LicenseCreatorService()
  Runtime.getRuntime.addShutdownHook(new Thread("service-shutdown") {
    override def run(): Unit = {
      info("Stopping service ...")
      service.stop()
      info("Cleaning up ...")
      service.destroy()
      info("Service stopped.")
    }
  })
  service.start()
  info("Service started ...")
}

class LicenseCreatorServlet extends ScalatraServlet with ApplicationSettings with DebugEnhancedLogging {
  get("/") {
    Ok("License Creator Service running")
  }

  post("/create") {
    contentType = "application/pdf"

    val parameters = new Parameters(
      templateResourceDir = new File(props.getString("license.resources")),
      datasetID = params("datasetId"),
      isSample = false,
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

    var success = false // TODO: get rid of var
    val output = new ByteArrayOutputStream()
      output
      .usedIn(LicenseCreator(parameters).createLicense)
      .doOnCompleted { success = true }
      .doOnTerminate {
        // close LDAP at the end of the main
        debug("closing ldap")
        parameters.ldap.close()
      }
      .toBlocking
      .subscribe(
        _ => {},
        e => logger.error("An error was caught in main:", e),
        () => debug("completed"))

    if(success) Ok(output.toByteArray)
    else InternalServerError() // TODO: distinguish between server errors and client errors
  }
}