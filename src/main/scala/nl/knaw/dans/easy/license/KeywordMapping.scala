package nl.knaw.dans.easy.license

// @formatter:off
trait KeywordMapping { val keyword: String }
case object DansManagedDoi        extends KeywordMapping { val keyword = "DansManagedDoi" }
case object DansManagedEncodedDoi extends KeywordMapping { val keyword = "DansManagedEncodedDoi" }
case object DateSubmitted         extends KeywordMapping { val keyword = "DateSubmitted" }
case object Title                 extends KeywordMapping { val keyword = "Title" }
case object UserName              extends KeywordMapping { val keyword = "Name" }
case object UserOrganisation      extends KeywordMapping { val keyword = "Organisation" }
case object UserAddress           extends KeywordMapping { val keyword = "Address" }
case object UserPostalCode        extends KeywordMapping { val keyword = "PostalCode" }
case object UserCity              extends KeywordMapping { val keyword = "City" }
case object UserCountry           extends KeywordMapping { val keyword = "Country" }
case object UserTelephone         extends KeywordMapping { val keyword = "Telephone" }
case object UserEmail             extends KeywordMapping { val keyword = "Email" }
// @formatter:on
