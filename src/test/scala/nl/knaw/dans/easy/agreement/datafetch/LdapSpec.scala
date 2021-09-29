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

import javax.naming.NamingEnumeration
import javax.naming.directory.{ Attributes, SearchControls, SearchResult }
import javax.naming.ldap.LdapContext
import nl.knaw.dans.easy.agreement.NoUserFoundException
import nl.knaw.dans.easy.agreement.fixture.TestSupportFixture
import org.scalamock.scalatest.MockFactory

import scala.util.{ Failure, Success }

class LdapSpec extends TestSupportFixture with MockFactory {

  "query" should "return the results of sending a query to LDAP" in {
    val testDepositorId = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]
    val attrs1 = mock[Attributes]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testDepositorId") }

    (ctx.search(_: String, _: String, _: SearchControls)) expects f returning result

    inSequence {
      result.hasMoreElements _ expects () returns true
      result.nextElement _ expects () returns new SearchResult("foobar1", null, attrs1)
    }

    val ldap = new LdapImpl(ctx)
    ldap.query(testDepositorId) should matchPattern { case Success(`attrs1`) => }
  }

  it should "fail when no results are given from LDAP" in {
    val testDepositorId = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]
    val attrs1 = mock[Attributes]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testDepositorId") }

    (ctx.search(_: String, _: String, _: SearchControls)) expects f returning result

    inSequence {
      result.hasMoreElements _ expects () returns false
    }

    val ldap = new LdapImpl(ctx)
    ldap.query(testDepositorId) should matchPattern { case Failure(NoUserFoundException(`testDepositorId`)) => }
  }
}
