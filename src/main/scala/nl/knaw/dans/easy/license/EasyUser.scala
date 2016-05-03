package nl.knaw.dans.easy.license

import javax.naming.directory.Attributes
import javax.naming.ldap.LdapContext

import rx.lang.scala.Observable

import scala.language.postfixOps

case class EasyUser(userID: UserID, name: String, organization: String, address: String,
                    postalCode: String, city: String, country: String, telephone: String,
                    email: String)

object EasyUser {

  def getByID(userID: UserID)(implicit ctx: LdapContext): Observable[EasyUser] = {
    queryLDAP(userID)(implicit attrs => {
      val name = getOrEmpty("displayname")
      val org = getOrEmpty("o")
      val addr = getOrEmpty("postaladdress")
      val code = getOrEmpty("postalcode")
      val place = getOrEmpty("l")
      val country = getOrEmpty("st")
      val phone = getOrEmpty("telephonenumber")
      val mail = getOrEmpty("mail")

      EasyUser(userID, name, org, addr, code, place, country, phone, mail)
    })
  }

  private def get(attrID: String)(implicit attrs: Attributes): Option[String] = {
    Option(attrs get attrID).map(_.get.toString)
  }

  private def getOrEmpty(attrID: String)(implicit attrs: Attributes): String = get(attrID) getOrElse ""
}

