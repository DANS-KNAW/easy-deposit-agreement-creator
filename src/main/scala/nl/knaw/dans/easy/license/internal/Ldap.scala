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
package nl.knaw.dans.easy.license.internal

import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls}
import javax.naming.ldap.LdapContext

import nl.knaw.dans.easy.license.DepositorID
import rx.lang.scala.Observable

trait Ldap extends AutoCloseable {

  /**
    * Queries LDAP for the user data corresponding to the given `depositorID`
    *
    * @param depositorID the identifier related to the depositor
    * @return the result of the query in key-value pairs
    */
  def query(depositorID: DepositorID): Observable[Attributes]
}

case class LdapImpl(ctx: LdapContext) extends Ldap {

  implicit class NamingEnumerationToObservable[T](val enum: NamingEnumeration[T]) {
    def toObservable: Observable[T] = Observable.from(new Iterable[T] {
      def iterator = new Iterator[T] {
        def hasNext: Boolean = enum.hasMore

        def next(): T = enum.next()
      }
    })
  }

  def query(depositorID: DepositorID): Observable[Attributes] = {
    Observable.defer {
      val searchFilter = s"(&(objectClass=easyUser)(uid=$depositorID))"
      val searchControls = {
        val sc = new SearchControls()
        sc.setSearchScope(SearchControls.SUBTREE_SCOPE)
        sc
      }

      ctx.search("dc=dans,dc=knaw,dc=nl", searchFilter, searchControls)
        .toObservable
        .map(_.getAttributes)
    }
  }

  def close(): Unit = ctx.close()
}
