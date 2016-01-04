package edu.tum.cs.isabelle.setup

import java.net.URL
import java.nio.channels.FileChannel
import java.nio.file._

import org.apache.commons.lang3.SystemUtils

import edu.tum.cs.isabelle.api.{BuildInfo, Version}

import acyclic.file

/**
 * Detection of the machine's [[Platform platform]].
 *
 * Currently, only Linux and Windows are supported.
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

  /** Universal OS X platform for both 32- and 64-bit machines. */
  case object OSX extends OfficialPlatform("macos") {
    def localStorage =
      Paths.get(System.getProperty("user.home")).resolve("Library/Preferences/libisabelle").toAbsolutePath
  }

  /** Make an educated guess at the platform, not guaranteed to be correct. */
  def guess: Option[OfficialPlatform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else if (SystemUtils.IS_OS_WINDOWS)
      Some(Windows)
    else if (SystemUtils.IS_OS_MAC_OSX)
      Some(OSX)
    else
      None

  def genericPlatform(localStorage0: Path): Platform =
    new Platform {
      val localStorage = localStorage0.toAbsolutePath
    }

}

/**
 * The underlying operating system platform with knowlege of a local storage
 * path.
 *
 * It is recommended to obtain instances via the
 * [[Platform$ companion object]].
 */
sealed abstract class Platform {

  def localStorage: Path

  final def setupStorage: Path =
    localStorage.resolve("setups")

  final def setupStorage(version: Version): Path =
    setupStorage.resolve(s"Isabelle${version.identifier}")

  final def versionedStorage: Path =
    localStorage.resolve(s"v${BuildInfo.version}")

  final def lockFile: Path =
    localStorage.resolve(".lock")

  def withLock[A](f: () => A): A = {
    Files.createDirectories(localStorage)
    Option(FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock()) match {
      case None =>
        sys.error("lock could not be acquired")
      case Some(lock) =>
        try {
          f()
        }
        finally {
          lock.close()
        }
    }
  }

}

/**
 * A `[[Platform]]` with known archive location.
 *
 * Official platforms can be installed and bootstrapped automatically via the
 * appropriate methods in [[Setup$ `Setup`]].
 */
sealed abstract class OfficialPlatform private[isabelle](val name: String) extends Platform {

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
