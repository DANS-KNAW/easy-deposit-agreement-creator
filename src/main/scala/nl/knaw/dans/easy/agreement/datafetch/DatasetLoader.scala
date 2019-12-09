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
package nl.knaw.dans.easy.agreement.datafetch

import javax.naming.directory.Attributes
import nl.knaw.dans.easy.agreement.{ DatasetId, DepositorId }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import nl.knaw.dans.pf.language.emd.EasyMetadataImpl
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller

import scala.util.Try
import scala.xml.XML

trait DatasetLoader {

  /**
   * Create a `Dataset` based on the given `datasetID`
   *
   * @param datasetId the identifier of the dataset
   * @return the dataset corresponding to `datasetID`
   */
  def getDatasetById(datasetId: DatasetId): Try[Dataset]

  /**
   * Queries the user data given a `depositorID`
   *
   * @param depositorId the identifier of the user
   * @return the user data corresponding to the `depositorID`
   */
  def getUserById(depositorId: DepositorId): Try[EasyUser]
}

class DatasetLoaderImpl(fedora: Fedora, ldap: Ldap) extends DatasetLoader with DebugEnhancedLogging {

  override def getDatasetById(datasetId: DatasetId): Try[Dataset] = {
    trace(datasetId)
    for {
      emdIS <- fedora.getEMD(datasetId)
      // TODO test what kind of exception it produces when 'emdIS' does not contain a proper EMD
      emd <- resource.managed(emdIS).map(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal).tried
      amdIS <- fedora.getAMD(datasetId)
      depositorId <- resource.managed(amdIS).map(is => (XML.load(is) \\ "depositorId").text).tried
      _ = debug(s"found depositor '$depositorId' with dataset $datasetId")
      depositor <- getUserById(depositorId)
    } yield Dataset(datasetId, emd, depositor)
  }

  override def getUserById(depositorId: DepositorId): Try[EasyUser] = {
    trace(depositorId)
    ldap.query(depositorId)
      .map(implicit attrs => {
        val name = getOrEmpty("displayname")
        val org = getOrEmpty("o")
        val addr = getOrEmpty("postaladdress")
        val code = getOrEmpty("postalcode")
        val place = getOrEmpty("l")
        val country = getOrEmpty("st")
        val phone = getOrEmpty("telephonenumber")
        val mail = getOrEmpty("mail")

        EasyUser(name, org, addr, code, place, country, phone, mail)
      })
  }

  private def getOrEmpty(attrID: String)(implicit attrs: Attributes) = {
    Option(attrs get attrID).fold("")(_.get.toString)
  }
}
