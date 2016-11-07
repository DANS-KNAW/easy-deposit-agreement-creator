package nl.knaw.dans.easy.license

import org.rogach.scallop.ScallopConf

trait CLISpec[T <: ScallopConf] {

  def cli(args: Array[String]): T

  def verifiedCLI(args: Array[String]): T = {
    val opts = cli(args)
    opts.verify()
    opts
  }

  def commandLineErrorMessage(args: Array[String]): String = {
    var errorMessage: String = ""
    val opts = cli(args)
    opts.errorMessageHandler = (s: String) => errorMessage = s

    opts.verify()
    errorMessage
  }
}
