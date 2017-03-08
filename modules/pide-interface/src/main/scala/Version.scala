package info.hupel.isabelle.api

sealed trait Version

object Version {

  case object Devel extends Version

  /**
   * Represents the version of a stable Isabelle release.
   */
  final case class Stable(identifier: String) extends Version {
    final override def toString: String =
      s"<Isabelle$identifier>"
  }

}
