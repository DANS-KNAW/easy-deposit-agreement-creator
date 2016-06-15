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

import java.io.{File, OutputStream, OutputStreamWriter}
import java.nio.charset.Charset
import java.util.Properties

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.slf4j.LoggerFactory

import scala.util.Try

trait TemplateResolver {

  /**
    * Create the template and write it to `out` after filling in the placeholders with `map`.
    *
    * @param out The `OutputStream` where the filled in template is written to
    * @param map The mapping between placeholders and actual values
    * @param encoding The encoding to be wused in writing to `out`
    * @return `Success` if filling in the template succeeded, `Failure` otherwise
    */
  def createTemplate(out: OutputStream, map: PlaceholderMap, encoding: Charset = encoding): Try[Unit]
}
class VelocityTemplateResolver(properties: Properties)(implicit parameters: Parameters) extends TemplateResolver {

  val log = LoggerFactory.getLogger(getClass)

  val velocityResources = new File(properties.getProperty("file.resource.loader.path"))
  val templateFileName = properties.getProperty("template.file.name")

  log.debug(s"template folder: $velocityResources")

  val doc = new File(velocityResources, templateFileName)
  assert(doc.exists(), s"file does not exist - $doc")

  val engine = {
    val engine = new VelocityEngine(properties)
    engine.init()
    engine
  }

  def createTemplate(out: OutputStream, map: PlaceholderMap, encoding: Charset = encoding) = {
    log.debug("resolve template placeholders")

    new OutputStreamWriter(out).use(writer => {
      val context = new VelocityContext
      map.foreach { case (kw, o) => context.put(kw.keyword, o) }

      engine.getTemplate(templateFileName, encoding.displayName()).merge(context, writer)
    })
  }
}
