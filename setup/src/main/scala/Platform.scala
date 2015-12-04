package edu.tum.cs.isabelle.setup

import java.net.URL
import java.nio.file.{Path, Paths}

import org.apache.commons.lang3.SystemUtils

import edu.tum.cs.isabelle.api.{BuildInfo, Version}

import acyclic.file

/**
 * Detection of the machine's [[Platform platform]].
 *
 * Currently, only Linux is supported.
 */
object Platform {

  /** Universal Linux platform for both 32- and 64-bit machines. */
  case object Linux extends OfficialPlatform("linux") {
    def localStorage =
      Paths.get(System.getProperty("user.home")).resolve(".local/share/libisabelle").toAbsolutePath
  }

  /** Universal Windows platform for both 32- and 64-bit machines. */
  case object Windows extends OfficialPlatform("windows") {
    def localStorage =
      Paths.get(System.getenv("LOCALAPPDATA")).resolve("libisabelle").toAbsolutePath
  }

  /** Make an educated guess at the platform, not guaranteed to be correct. */
  def guess: Option[Platform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else if (SystemUtils.IS_OS_WINDOWS)
      Some(Windows)
    else
      None

  def genericPlatform(name: String, localStorage0: Path): Platform =
    new Platform(name) {
      val localStorage = localStorage0.toAbsolutePath
    }

}

/**
 * Wrapper around Isabelle's platform identifiers.
 *
 * It is recommended to obtain instances via the
 * [[Platform$ companion object]].
 */
sealed abstract class Platform(val name: String) {

  def localStorage: Path

  final def setupStorage: Path =
    localStorage.resolve("setups")

  final def setupStorage(version: Version): Path =
    setupStorage.resolve(s"Isabelle${version.identifier}")

  final def versionedStorage: Path =
    localStorage.resolve(s"v${BuildInfo.version}")

}

sealed abstract class OfficialPlatform private[isabelle](name: String) extends Platform(name) {

  /** Default base URL pointing to the standard Isabelle server. */
  protected def baseURL(version: Version) =
    s"https://isabelle.in.tum.de/website-Isabelle${version.identifier}/dist/Isabelle${version.identifier}"

  /**
   * Internet location containing an archive of the requested
   * [[edu.tum.cs.isabelle.api.Version version]] for this platform, if
   * available.
   */
  def url(version: Version): Option[URL] =
    Some(new URL(s"${baseURL(version)}_$name.tar.gz"))

}
