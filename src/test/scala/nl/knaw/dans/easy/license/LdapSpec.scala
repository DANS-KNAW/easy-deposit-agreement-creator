/**
 * Copyright (C) 2016 DANS - Data Archiving and Networked Services (info@dans.knaw.nl)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package nl.knaw.dans.easy.license

import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls, SearchResult}
import javax.naming.ldap.LdapContext

import org.scalamock.scalatest.MockFactory
import rx.lang.scala.observers.TestSubscriber

class LdapSpec extends UnitSpec with MockFactory {

  "query" should "return the results of sending a query to LDAP" in {
    val testDepositorID = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]
    val attrs1 = mock[Attributes]
    val attrs2 = mock[Attributes]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testDepositorID") }

    (ctx.search(_: String, _: String, _: SearchControls)) expects f returning result

    inSequence {
      // the first hasMore call has to do with the implementation of Observable.from...
      result.hasMore _ expects () returns true once()
      result.hasMore _ expects () returns true twice()
      result.hasMore _ expects () returns false once()
    }

    inSequence {
      result.next _ expects () returns new SearchResult("foobar1", null, attrs1)
      result.next _ expects () returns new SearchResult("foobar2", null, attrs2)
    }

    val ldap = new LdapImpl(ctx)
    val testSubscriber = TestSubscriber[Attributes]
    ldap.query(testDepositorID)(identity).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertValues(attrs1, attrs2)
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }

  it should "return an Observable with no onNext calls when the query has no results" in {
    val testDepositorID = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testDepositorID") }

    (ctx.search(_: String, _: String, _: SearchControls)) expects f returning result

    result.hasMore _ expects () returns false once()

    val ldap = new LdapImpl(ctx)
    val testSubscriber = TestSubscriber[Attributes]
    ldap.query(testDepositorID)(identity).subscribe(testSubscriber)

    testSubscriber.awaitTerminalEvent()
    testSubscriber.assertNoValues()
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
  }
}
