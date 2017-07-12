package info.hupel.isabelle.api

sealed trait Version

object Version {

  val Compound = "(stable:|devel:|)([a-zA-Z0-9-_]+)".r

  def parse(version: String): Either[String, Version] = version match {
    case Compound("stable:" | "", id) => Right(Stable(id))
    case Compound("devel:", id) => Right(Devel(id))
    case _ => Left(s"malformed version: $version")
  }

  final case class Devel(identifier: String) extends Version {
    final override def toString: String =
      s"<Isabelle(devel)$identifier>"
  }

  /**
   * Represents the version of a stable Isabelle release.
   */
  final case class Stable(identifier: String) extends Version {
    final override def toString: String =
      s"<Isabelle$identifier>"
  }

}
