package info.hupel.isabelle.api

import java.nio.file.Path

/**
 * Represents the location and name of a ''session'' (Isabelle terminology).
 *
 * Refer to the Isabelle system manual for details about sessions.
 * `libisabelle` assumes that users are familiar with the session handling
 * of Isabelle.
 *
 * Creation of configurations is completely unchecked. Errors such as
 * non-existing paths will only manifest themselves when attempting to
 * [[info.hupel.isabelle.System.build build]] a configuration or
 * [[info.hupel.isabelle.System.create create]] a
 * [[info.hupel.isabelle.System system]]. Nonetheless, users should go
 * through an existing [[info.hupel.isabelle.setup.Resources$ resources]]
 * object.
 */
final case class Configuration(paths: List[Path], session: String) {
  override def toString: String =
    s"session $session" + (paths match {
      case Nil => ""
      case ps => " at " + ps.mkString(":")
    })
}
