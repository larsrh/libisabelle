package edu.tum.cs.isabelle.api

import java.nio.file.Path

import acyclic.file

/**
 * Represents the version of an Isabelle release.
 *
 * Repository snapshots are not supported.
 */
final case class Version(identifier: String) {
  final override def toString: String =
    s"<Isabelle$identifier>"
}
