package edu.tum.cs.isabelle.setup

import java.net.URL
import java.nio.file.{Files, Path, Paths}

import org.apache.commons.lang3.SystemUtils

object Platform {
  case object Linux extends Platform("linux")

  def guess: Option[Platform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else
      None
}

sealed abstract class Platform(val name: String)

object Version {
  case object Isa2014 extends Version("2014")
  val latest = Isa2014

  val all = List(Isa2014)
}

sealed abstract class Version(val identifier: String) {

  private lazy val baseURL =
    s"https://isabelle.in.tum.de/website-Isabelle$identifier/dist/Isabelle$identifier"

  def url(platform: Platform): Option[URL] = platform match {
    case Platform.Linux =>
      Some(new URL(s"${baseURL}_linux.tar.gz"))
  }

  def detectEnvironment(base: Path): Option[Environment] = {
    val path = base resolve s"Isabelle$identifier"
    if (Files.isDirectory(path))
      Some(Environment(path, this))
    else
      None
  }

  override def toString: String =
    s"<Isabelle$identifier>"

}

case class Environment(home: Path, version: Version) {
  override def toString: String =
    s"$version at $home"
}

object Configuration {
  def fromPath(path: Path, session: String) =
    Configuration(Some(path), session)
}

case class Configuration(path: Option[Path], session: String) {
  override def toString: String =
    s"session $session" + path.map(p => s" at $p").getOrElse("")
}
