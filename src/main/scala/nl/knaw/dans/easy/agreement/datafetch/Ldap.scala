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
package nl.knaw.dans.easy.agreement.datafetch

import javax.naming.directory.{ Attributes, SearchControls }
import javax.naming.ldap.LdapContext
import nl.knaw.dans.easy.agreement.{ DepositorId, LdapException, NoUserFoundException }

import scala.collection.JavaConverters._
import scala.util.{ Failure, Try }

trait Ldap {

  /**
   * Queries LDAP for the user data corresponding to the given `depositorID`
   *
   * @param depositorID the identifier related to the depositor
   * @return the result of the query in key-value pairs
   */
  def query(depositorID: DepositorId): Try[Attributes]
}

class LdapImpl(ctx: LdapContext) extends Ldap {

  override def query(depositorId: DepositorId): Try[Attributes] = Try {
    val searchFilter = s"(&(objectClass=easyUser)(uid=$depositorId))"
    val searchControls = new SearchControls() {
      setSearchScope(SearchControls.SUBTREE_SCOPE)
    }

    ctx.search("dc=dans,dc=knaw,dc=nl", searchFilter, searchControls)
      .asScala
      .toStream
      .map(_.getAttributes)
      .headOption
      .getOrElse { throw NoUserFoundException(depositorId) }
  } recoverWith {
    case e: IllegalArgumentException => Failure(LdapException(depositorId, e))
  }
}
