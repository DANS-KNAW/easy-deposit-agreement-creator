/*
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
package nl.knaw.dans.easy.agreement

import javax.naming.ldap.{ InitialLdapContext, LdapContext }
import nl.knaw.dans.easy.agreement.datafetch.{ DatasetLoaderImpl, Fedora, FedoraImpl, LdapImpl }
import nl.knaw.dans.lib.logging.DebugEnhancedLogging
import scalaj.http.BaseHttp

import scala.util.{ Failure, Success, Try }

class EasyDepositAgreementCreatorApp(configuration: Configuration) extends DebugEnhancedLogging {

  private object Http extends BaseHttp(userAgent = s"easy-deposit-agreement-creator/${ configuration.version }")

  private val fedora: Fedora = new FedoraImpl(configuration.fedoraClient)
  private val generator: AgreementGenerator = new AgreementGenerator(Http, configuration.pdfGenerator)

  private def ldapContext(): LdapContext = new InitialLdapContext(configuration.ldapEnv, null)

  def validateDatasetIdExistsInFedora(datasetId: DatasetId): Try[Unit] = {
    logger.info(s"check if dataset $datasetId exists")
    fedora.datasetIdExists(datasetId)
      .flatMap {
        case true => Success(())
        case false => Failure(new NoSuchElementException(s"DatasetId $datasetId does not exist"))
      }
  }

  def createAgreement(datasetId: DatasetId, isSample: Boolean)(outputStreamProvider: OutputStreamProvider): Try[Unit] = {
    logger.info(s"creating agreement for dataset '$datasetId'")
    resource.managed(ldapContext())
      .map(ldapContext => new DatasetLoaderImpl(fedora, new LdapImpl(ldapContext)))
      .map(dataLoader => for {
        dataset <- dataLoader.getDatasetById(datasetId)
        _ <- generator.generate(dataset, isSample)(outputStreamProvider)
      } yield ())
      .tried
      .flatten
  }
}
