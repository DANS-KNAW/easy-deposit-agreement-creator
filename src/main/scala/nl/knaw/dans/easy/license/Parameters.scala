package nl.knaw.dans.easy.license

import java.io.File

// this class needs to be in a separate file rather than in package.scala because of interop with
// java business layer.
case class Parameters(templateResourceDir: File,
                      pdfScript: File,
                      datasetID: DatasetID,
                      isSample: Boolean,
                      fedora: Fedora,
                      ldap: Ldap) {
  override def toString: String = s"Parameters($templateResourceDir, $pdfScript, $datasetID, $isSample)"
}
