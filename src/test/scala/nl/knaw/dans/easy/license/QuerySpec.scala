package nl.knaw.dans.easy.license

import javax.naming.NamingEnumeration
import javax.naming.directory.{Attributes, SearchControls, SearchResult}
import javax.naming.ldap.LdapContext

import org.scalamock.scalatest.MockFactory
import org.scalatest.FlatSpec
import rx.lang.scala.observers.TestSubscriber

class QuerySpec extends FlatSpec with MockFactory {

  "queryLDAP" should "return the results of sending a query to LDAP" in {
    val testUserID = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]
    val attrs1 = mock[Attributes]
    val attrs2 = mock[Attributes]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testUserID") }

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

    val testSubscriber = TestSubscriber[Attributes]
    queryLDAP(testUserID)(identity)(ctx).subscribe(testSubscriber)

    testSubscriber.assertValues(attrs1, attrs2)
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
    testSubscriber.assertUnsubscribed()
  }

  it should "return an Observable with no onNext calls when the query has no results" in {
    val testUserID = "foobar"

    val ctx = mock[LdapContext]
    val result = mock[NamingEnumeration[SearchResult]]

    val f = where { (_: String, filter: String, _: SearchControls) => filter.contains(s"uid=$testUserID") }

    (ctx.search(_: String, _: String, _: SearchControls)) expects f returning result

    result.hasMore _ expects () returns false once()

    val testSubscriber = TestSubscriber[Attributes]
    queryLDAP(testUserID)(identity)(ctx).subscribe(testSubscriber)

    testSubscriber.assertNoValues()
    testSubscriber.assertNoErrors()
    testSubscriber.assertCompleted()
    testSubscriber.assertUnsubscribed()
  }
}
