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

import java.io.{File, PrintWriter}
import java.net.URL

import org.rogach.scallop.ScallopConf

class CommandLineOptions (args: Array[String]) extends ScallopConf(args) {
  printedName = "easy-license-creator";
  val __________ = " " * printedName.size

  version(s"$printedName v${Version()}")
  banner(s"""
           |<Replace with one sentence describing the main task of this module>
           |
           |Usage:
           |
           |$printedName <synopsis of command line parameters>
           |${__________} <...possibly continued here>
           |
           |Options:
           |""".stripMargin)
  //val url = opt[String]("someOption", noshort = true, descr = "Description of the option", default = Some("Default value"))
  footer("")
}

object CommandLineOptions {

  def parse(args: Array[String]): Parameters = {
    val opts = new CommandLineOptions(args)
    // Fill Parameters with values from command line
    Parameters()
  }
}
