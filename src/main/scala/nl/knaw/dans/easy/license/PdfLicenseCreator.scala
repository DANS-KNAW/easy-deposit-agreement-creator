package nl.knaw.dans.easy.license

import java.io.{InputStream, OutputStream}

import scala.sys.process._

object PdfLicenseCreator {

  def createPdf(input: InputStream, output: OutputStream)(implicit parameters: Parameters): ProcessBuilder = {
    val cmd = "weasyprint -f pdf - -"
    val command = parameters.vagrant match {
      case SSHConnection(userhost, privateKeyFile) => s"ssh -i $privateKeyFile $userhost $cmd"
      case LocalConnection => cmd
    }

    command #< input #> output
  }
}
