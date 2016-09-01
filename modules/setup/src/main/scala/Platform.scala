package info.hupel.isabelle.setup

import java.net.URL
import java.nio.channels.{FileChannel, FileLock}
import java.nio.file._

import scala.concurrent.{ExecutionContext, Future}
import scala.util.control.NonFatal

import org.apache.commons.lang3.SystemUtils

import org.log4s._

import info.hupel.isabelle.api.{BuildInfo, Version}

import acyclic.file

/**
 * Detection of the machine's [[Platform platform]].
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

  /**
   * Arbitrary platform based on a [[Platform#localStorage local storage]]
   * path.
   *
   * The main purpose is when using a custom (unmanaged) Isabelle installation.
   */
  def genericPlatform(localStorage0: Path): Platform =
    new Platform {
      val localStorage = localStorage0.toAbsolutePath
    }

}

/**
 * The underlying operating system platform with knowlege of a
 * [[localStorage local storage]] path.
 *
 * It is recommended to obtain instances via the
 * [[Platform$ companion object]].
 */
sealed abstract class Platform {

  private val logger = getLogger

  /**
   * Path where `libisabelle` stores files downloaded from the Internet, e.g.
   * by a [[Resolver resolver]] or by [[Setup.install installing]] Isabelle.
   */
  def localStorage: Path

  final def setupStorage: Path =
    localStorage.resolve("setups")

  final def setupStorage(version: Version): Path =
    setupStorage.resolve(s"Isabelle${version.identifier}")

  final def versionedStorage: Path =
    localStorage.resolve(s"v${BuildInfo.version}")

  final def lockFile: Path =
    localStorage.resolve(".lock")

  private def acquireLock(): Option[FileLock] = {
    Files.createDirectories(localStorage)
    FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE).tryLock() match {
      case null =>
        logger.warn("lock could not be acquired")
        None
      case lock =>
        Some(lock)
    }
  }

  def withLock[A](f: () => A): Option[A] = acquireLock().map { lock =>
    try {
      f()
    }
    finally {
      lock.close()
    }
  }

  def withAsyncLock[A](f: () => Future[A])(implicit ec: ExecutionContext): Future[A] = acquireLock() match {
    case None =>
      Future.failed(new RuntimeException("lock could not be acquired"))
    case Some(lock) =>
      f().map { a =>
        try { lock.close() } catch { case NonFatal(_) => }
        a
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
   * HTTP location containing an archive of the requested
   * [[info.hupel.isabelle.api.Version version]] for this platform.
   */
  def url(version: Version): URL =
    new URL(s"${baseURL(version)}_$name.tar.gz")

}
