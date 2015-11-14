package edu.tum.cs.isabelle.setup

import java.net.URL

import org.apache.commons.lang3.SystemUtils

import edu.tum.cs.isabelle.api.Version

import acyclic.file

/**
 * Detection of the machine's [[Platform platform]].
 *
 * Currently, only Linux is supported.
 */
object Platform {

  /** Universal Linux platform for both 32- and 64-bit machines. */
  case object Linux extends Platform("linux")

  /** Universal Windows platform for both 32- and 64-bit machines. */
  case object Windows extends Platform("windows")

  /** Make an educated guess at the platform, not guaranteed to be correct. */
  def guess: Option[Platform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else if (SystemUtils.IS_OS_WINDOWS)
      Some(Windows)
    else
      None

}

/**
 * Wrapper around Isabelle's platform identifiers.
 *
 * It is recommended to obtain instances via the
 * [[Platform$ companion object]]. No guarantees are made when constructing
 * instances manually.
 */
sealed abstract class Platform(val name: String) {

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
