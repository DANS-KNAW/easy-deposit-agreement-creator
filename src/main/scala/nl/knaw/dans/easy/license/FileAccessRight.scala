package nl.knaw.dans.easy.license

object FileAccessRight extends Enumeration {
  type FileAccessRight = Value

  val
  ANONYMOUS, // a user that is not logged in
  KNOWN, // a logged in user
  RESTRICTED_REQUEST, // a user that received permission to access the dataset
  RESTRICTED_GROUP, // a user belonging to the same group as the dataset
  NONE // none of the above
  = Value

  def valueOf(s: String) = FileAccessRight.values.find(_.toString == s)
}
