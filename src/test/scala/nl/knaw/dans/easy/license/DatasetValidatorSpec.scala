package nl.knaw.dans.easy.license

class DatasetValidatorSpec extends UnitSpec {

  "validate" should "replace null fields in easy-user with an empty string" in {
    val depositor = new EasyUser("foo", null, "addr", "zipcode", "ct", null, null, "bar")
    val expected = new EasyUser("foo", "", "addr", "zipcode", "ct", "", "", "bar")

    DatasetValidator.validate(depositor) shouldBe expected
  }
}
