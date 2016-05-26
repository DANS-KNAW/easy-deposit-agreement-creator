package nl.knaw.dans.easy.license

import java.io.File

/**
  * This class is used to identify whether the program is run on the server (on deasy, teasy or easy01) or on a private computer.
  * In the former case `LocalConnection` is used, whereas the latter maps to `SSHConnection`.
  */
sealed abstract class VagrantConnection {
  /**
    * Applies `f` if this is an `SSHConnection` and applies `ifLocal` if this is a `LocalConnection`.
    *
    * @param ifLocal the value to be returned when this is `LocalConnection`
    * @param f the function to be applied when this is `SSHConnection`
    * @tparam B the return type of both arguments and the return type of this function
    * @return the result of applying either `f` or `ifLocal`
    */
  def fold[B](ifLocal: => B)(f: (String, File) => B): B
}

case class SSHConnection(userhost: String, privateKeyFile: File) extends VagrantConnection {
  require(privateKeyFile.exists(), s"the private key file ($privateKeyFile) should exist")

  def fold[B](ifEmpty: => B)(f: (String, File) => B) = f(userhost, privateKeyFile)
}

case object LocalConnection extends VagrantConnection {
  def fold[B](ifLocal: => B)(f: (String, File) => B) = ifLocal
}
