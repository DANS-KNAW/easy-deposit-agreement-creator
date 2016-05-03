package nl.knaw.dans.easy.license

import javax.naming.ldap.LdapContext

import com.yourmediashelf.fedora.client.FedoraClient
import nl.knaw.dans.pf.language.emd.{EasyMetadata, EasyMetadataImpl}
import nl.knaw.dans.pf.language.emd.binding.EmdUnmarshaller
import rx.lang.scala.Observable

import scala.language.postfixOps
import scala.xml.XML

case class Dataset(datasetID: DatasetID, emd: EasyMetadata, easyUser: EasyUser)

object Dataset {
  def getDatasetByID(datasetID: DatasetID, userID: UserID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
    val emd = queryEMD(datasetID).single
    val user = EasyUser.getByID(userID).single

    emd.combineLatestWith(user)(Dataset(datasetID, _, _))
  }

  def getDatasetByID(datasetID: DatasetID)(implicit ctx: LdapContext, client: FedoraClient): Observable[Dataset] = {
    for {
      userID <- queryAMDForDepositorID(datasetID).single
      dataset <- getDatasetByID(datasetID, userID)
    } yield dataset
  }

  private def queryEMD(datasetID: DatasetID)(implicit client: FedoraClient): Observable[EasyMetadata] = {
    queryFedora(datasetID, "EMD")(new EmdUnmarshaller(classOf[EasyMetadataImpl]).unmarshal)
  }

  private def queryAMDForDepositorID(datasetID: DatasetID)(implicit client: FedoraClient): Observable[UserID] = {
    queryFedora(datasetID, "AMD")(stream => XML.load(stream) \\ "depositorID" text)
  }
}
