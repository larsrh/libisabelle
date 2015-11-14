package edu.tum.cs.isabelle.api

import java.nio.file.Path

import acyclic.file

/** Convenience constructors for [[Configuration configurations]]. */
object Configuration {
  /**
   * Creates a [[Configuration configuration]] from an additional path, that
   * is, the specified session is declared in a `ROOT` file in that path
   * (or indirectly via a `ROOTS` file).
   *
   * In almost all cases, it should refer to a session which has one of the
   * `Protocol` session of the
   * [[https://github.com/larsrh/libisabelle-protocol accompanying theory sources]]
   * as an ancestor, or includes these theories in some other way.
   *
   * The given path must not be identical to or be a subdirectory of the
   * Isabelle home path. It must also, either directly or indirectly via a
   * `ROOTS` file, contain declarations for all ancestor sessions.
   */
  def fromPath(path: Path, session: String) =
    Configuration(Some(path), session)

  /**
   * Creates a [[Configuration configuration]] with an empty path, that is,
   * it must be a session included in the Isabelle distribution.
   *
   * Unless using a custom Isabelle distribution, a
   * [[edu.tum.cs.isabelle.System.create system created]] with such a
   * configuration will be unable to reply to any
   * [[edu.tum.cs.isabelle.Operation operation]].
   */
  def fromBuiltin(session: String) =
    Configuration(None, session)
}

/**
 * Represents the location and name of a ''session'' (Isabelle terminology).
 *
 * Refer to the Isabelle system manual for details about sessions.
 * `libisabelle` assumes that users are familiar with the session handling
 * of Isabelle.
 *
 * Creation of configurations is completely unchecked. Errors such as
 * non-existing paths will only manifest themselves when attempting to
 * [[edu.tum.cs.isabelle.System.build build]] a configuration or
 * [[edu.tum.cs.isabelle.System.create create]] a
 * [[edu.tum.cs.isabelle.System system]]. Nonetheless, users should go
 * through one of the constructors in the
 * [[Configuration$ companion object]].
 */
case class Configuration(path: Option[Path], session: String) {
  override def toString: String =
    s"session $session" + path.map(p => s" at $p").getOrElse("")
}
