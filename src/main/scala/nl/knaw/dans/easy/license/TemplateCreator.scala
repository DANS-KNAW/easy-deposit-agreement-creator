package nl.knaw.dans.easy.license

import java.io.{File, FileInputStream, FileWriter}
import java.nio.charset.Charset
import java.util.Properties

import org.apache.velocity.VelocityContext
import org.apache.velocity.app.VelocityEngine
import org.apache.velocity.exception.MethodInvocationException

import scala.collection.JavaConverters._
import scala.util.Try

class TemplateCreator(propertiesFile: File) {

  val properties = {
    val properties = new Properties
    properties.load(new FileInputStream(propertiesFile))
    properties
  }
  val velocityResources = new File(properties.getProperty("file.resource.loader.path"))
  val templateFileName = properties.getProperty("template.file.name")
  val doc = new File(velocityResources, templateFileName)

  assert(doc.exists(), s"file does not exist - $doc")

  val engine = {
    val engine = new VelocityEngine(properties)
    engine.init()
    engine
  }

  /**
    * Create the template on location `templateFile` after filling in the placeholders with `map`.
    * If an `Exception` occurs, `templateFile` is not created/deleted.
    *
    * @param templateFile The location where to store the template
    * @param map The mapping between placeholders and actual values
    * @param encoding the encoding to be used in writing to `templateFile`
    * @return `Success` if creating a template succeeded, `Failure` otherwise
    */
  def createTemplate(templateFile: File, map: Map[KeywordMapping, Object], encoding: Charset = encoding) = {
    Try {
      val context = new VelocityContext(map.map { case (kw, o) => (kw.keyword, o) }.asJava)
      val writer = new FileWriter(templateFile)

      engine.getTemplate(doc.getName, encoding.displayName()).merge(context, writer)
      writer.flush()
      writer.close()
    }.doOnError(_ => templateFile.delete())
  }
}
