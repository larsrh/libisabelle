package edu.tum.cs.isabelle.setup

import java.net.URL

import org.apache.commons.lang3.SystemUtils

import edu.tum.cs.isabelle.api.Version

object Platform {
  case object Linux extends Platform("linux") {
    def url(version: Version): Option[URL] = 
      Some(new URL(s"${baseURL(version)}_linux.tar.gz"))
  }

  def guess: Option[Platform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else
      None
}

sealed abstract class Platform(val name: String) {

  protected def baseURL(version: Version) =
    s"https://isabelle.in.tum.de/website-Isabelle${version.identifier}/dist/Isabelle${version.identifier}"

  def url(version: Version): Option[URL]

}
