package edu.tum.cs.isabelle.api

import java.nio.file.Path

object Version {
  val Isa2014 = Version("2014")

  val latest = Isa2014
}

case class Version(identifier: String) {
  final override def toString: String =
    s"<Isabelle$identifier>"
}
