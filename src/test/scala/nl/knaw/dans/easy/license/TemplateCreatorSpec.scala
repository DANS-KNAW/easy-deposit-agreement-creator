package nl.knaw.dans.easy.license

import java.io.File

import org.apache.velocity.exception.MethodInvocationException
import org.scalamock.scalatest.MockFactory
import org.scalatest.{BeforeAndAfter, BeforeAndAfterAll}

import scala.util.{Failure, Success}

class TemplateCreatorSpec extends UnitSpec with MockFactory with BeforeAndAfter with BeforeAndAfterAll {

  implicit val parameters = new Parameters(null, new File(testDir, "template"), null, null, null, null, null)

  before {
    new File(getClass.getResource("/velocity/").toURI)
      .copyDir(parameters.templateDir)
  }

  after {
    parameters.templateDir.deleteDirectory()
  }

  override def afterAll = testDir.getParentFile.deleteDirectory()

  val keyword = new KeywordMapping { val keyword = "name" }

  "createTemplate" should """map the "name" keyword to "world" in the template and put the result in a file""" in {
    val templateCreator = new TemplateCreator(new File(parameters.templateDir, "velocity-test-engine.properties"))

    val map: Map[KeywordMapping, Object] = Map(keyword -> "world")
    val resFile = new File(testDir, "template/result.html")

    templateCreator.createTemplate(resFile, map) shouldBe a[Success[_]]

    resFile should exist
    resFile.read() should include ("<p>hello world</p>")
  }

  it should "fail if not all placeholders are filled in" in {
    val templateCreator = new TemplateCreator(new File(parameters.templateDir, "velocity-test-engine.properties"))

    val map: Map[KeywordMapping, Object] = Map.empty
    val resFile = new File(testDir, "template/result.html")

    val res = templateCreator.createTemplate(resFile, map)
    res shouldBe a[Failure[MethodInvocationException]]

    resFile shouldNot exist
  }
}
