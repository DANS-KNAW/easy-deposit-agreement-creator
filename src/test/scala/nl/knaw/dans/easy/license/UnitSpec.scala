package nl.knaw.dans.easy.license

import java.io.File

import org.scalatest._

abstract class UnitSpec extends FlatSpec with Matchers with OptionValues with Inside with Inspectors with OneInstancePerTest {

  val testDir = new File(s"target/test/${getClass.getSimpleName}")
}
