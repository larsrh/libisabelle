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
}

sealed abstract class Version(val identifier: String) {

  private lazy val baseURL =
    s"https://isabelle.in.tum.de/website-Isabelle$identifier/dist/Isabelle$identifier"

  def url(platform: Platform): Option[URL] = platform match {
    case Platform.Linux =>
      Some(new URL(s"${baseURL}_linux.tar.gz"))
  }

  private lazy val home =
    Paths.get(s"Isabelle$identifier")

  def detectInstall(base: Path): Option[Install] = {
    val path = base resolve home
    if (Files.isDirectory(path))
      Some(Install(path, this))
    else
      None
  }

  override def toString: String =
    s"<Isabelle$identifier>"

}

case class Install(home: Path, version: Version) {
  override def toString: String =
    s"$version at $home"
}
