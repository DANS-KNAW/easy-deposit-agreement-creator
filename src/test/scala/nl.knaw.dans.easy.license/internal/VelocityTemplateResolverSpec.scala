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

import java.io.{ByteArrayOutputStream, File}
import java.util.Properties

import nl.knaw.dans.easy.license.UnitSpec
import org.apache.velocity.exception.MethodInvocationException
import org.scalatest.exceptions.TestFailedException
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.util.{Failure, Success}

class VelocityTemplateResolverSpec extends UnitSpec with BeforeAndAfter with BeforeAndAfterAll {

  implicit val parameters = new BaseParameters(new File(testDir, "template"), null, false, 3)

  before {
    new File(getClass.getResource("/velocity/").toURI).copyDir(parameters.templateResourceDir)
  }

  after {
    parameters.templateResourceDir.deleteDirectory()
  }

  override def afterAll: Unit = testDir.getParentFile.deleteDirectory()

  private val keyword: KeywordMapping = new KeywordMapping { val keyword: String = "name" }
  private val testProperties = {
    val p = new Properties()

    p.setProperty("runtime.references.strict", "true")
    p.setProperty("file.resource.loader.path", "target/test/VelocityTemplateResolverSpec/template/")
    p.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute")
    p.setProperty("template.file.name", "LicenseTest.html")

    p
  }
  private val testPropertiesFail = {
    val p = new Properties()

    p.setProperty("runtime.references.strict", "true")
    p.setProperty("file.resource.loader.path", "target/test/VelocityTemplateResolverSpec/template/")
    p.setProperty("runtime.log.logsystem.class", "org.apache.velocity.runtime.log.NullLogChute")
    p.setProperty("template.file.name", "LicenseFailTest.html")

    p
  }

  "createTemplate" should """map the "name" keyword to "world" in the template and put the result in a file""" in {
    val templateCreator = new VelocityTemplateResolver(testProperties)

    val map: Map[KeywordMapping, Object] = Map(keyword -> "world")
    val baos = new ByteArrayOutputStream()

    templateCreator.createTemplate(baos, map) shouldBe a[Success[_]]

    new String(baos.toByteArray) should include ("<p>hello world</p>")
  }

  it should "fail if not all placeholders are filled in" in {
    val templateCreator = new VelocityTemplateResolver(testProperties)

    val map: Map[KeywordMapping, Object] = Map.empty
    val baos = new ByteArrayOutputStream()

    val res = templateCreator.createTemplate(baos, map)
    res shouldBe a[Failure[_]]
    (the [MethodInvocationException] thrownBy res.get).getMessage should include ("$name")

    new String(baos.toByteArray) should not include "<p>hello world</p>"
  }

  it should "fail when the template does not exist" in {
    try {
      new VelocityTemplateResolver(testPropertiesFail)
      fail("an error should have been thrown, but this was not the case.")
    }
    catch {
      case e: TestFailedException => throw e // propagate failure
      case _: Throwable => new File(testDir, "template/result.html") shouldNot exist
    }
  }
}
