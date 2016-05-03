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

import org.slf4j.LoggerFactory
import nl.knaw.dans.easy.license.{CommandLineOptions => cmd}

object Command {
  val log = LoggerFactory.getLogger(getClass)

  def main(args: Array[String]): Unit = {
    log.debug("Starting command line interface")
    implicit val ps = cmd.parse(args)

    // Here, pass the parameters to the main application logic (possibly to an Akka actor).

    ps.ldap.close()
  }
}
