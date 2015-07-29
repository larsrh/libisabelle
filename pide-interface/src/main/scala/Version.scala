package edu.tum.cs.isabelle.api

import java.nio.file.Path

case class Version(identifier: String) {
  final override def toString: String =
    s"<Isabelle$identifier>"
}
