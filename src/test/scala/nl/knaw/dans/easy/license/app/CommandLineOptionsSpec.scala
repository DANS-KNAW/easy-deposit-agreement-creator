package nl.knaw.dans.easy.license.app

import java.io.File

import nl.knaw.dans.easy.license.UnitSpec

class CommandLineOptionsSpec extends UnitSpec {

  "verify cli" should "succeed when given the correct parameters with short form for the opt" in {
    val args = "-s easy-dataset:1 src/test/resources/license.pdf".split(" ")
    val opts = new CommandLineOptions(args)
    opts.verify()

    opts.isSample() shouldBe true
    opts.datasetID() shouldBe "easy-dataset:1"
    opts.outputFile() shouldBe new File("src/test/resources/license.pdf")
  }

  it should "succeed when given the correct parameters with long form for the opt" in {
    val args = "--sample easy-dataset:1 src/test/resources/license.pdf".split(" ")
    val opts = new CommandLineOptions(args)
    opts.verify()

    opts.isSample() shouldBe true
    opts.datasetID() shouldBe "easy-dataset:1"
    opts.outputFile() shouldBe new File("src/test/resources/license.pdf")
  }

  it should "succeed when given the correct trailing parameters without giving the opt" in {
    val args = "easy-dataset:1 src/test/resources/license.pdf".split(" ")
    val opts = new CommandLineOptions(args)
    opts.verify()

    opts.isSample() shouldBe false
    opts.datasetID() shouldBe "easy-dataset:1"
    opts.outputFile() shouldBe new File("src/test/resources/license.pdf")
  }

  it should "fail when the outputFile is missing" in {
    val args = "easy-dataset:1".split(" ")
    var errorMessage: String = ""
    val opts = new CommandLineOptions(args) {
      errorMessageHandler = (s: String) => errorMessage = s
    }
    opts.verify()

    errorMessage should include ("license-file")
  }

  it should "fail when invalid arguments are given" in {
    val args = "--invalidArg easy-dataset:1 src/test/resources/license.pdf".split(" ")
    var errorMessage: String = ""
    val opts = new CommandLineOptions(args) {
      errorMessageHandler = (s: String) => errorMessage = s
    }
    opts.verify()

    errorMessage should include ("invalidArg")
  }
}
