package info.hupel.isabelle.setup

import java.nio.file.{Files, Path}

import scala.concurrent.Future
import scala.util._

import org.log4s._

import monix.execution.Scheduler

import info.hupel.isabelle.api._

/**
 * Detecting and creating [[Setup setups]].
 *
 * This object assumes that there is a ''base path'' in which all Isabelle
 * setups reside. Given a [[info.hupel.isabelle.api.Version version]], the
 * base path can either be searched for an existing setup, or an archive can
 * be downloaded from the Internet and extracted into the path.
 */
object Setup {

  /**
   * Common trait for a reason why a [[Setup setup]] could not be
   * [[detect detected]].
   */
  sealed trait NoSetup

  /**
   * Common trait for a reason why a [[Setup setup]] could not be
   * [[install installed]].
   */
  sealed trait SetupImpossible { def explain: String }

  case object Absent extends NoSetup
  case class Corrupted(path: Path) extends NoSetup with SetupImpossible {
    def explain = s"Possibly corrupted setup detected at $path; try deleting that folder and running again"
  }
  case class Busy(path: Path) extends NoSetup with SetupImpossible {
    def explain = s"File lock $path could not be acquired (busy)"
  }
  case object UnknownPlatform extends SetupImpossible {
    def explain = "Impossible to download setup on unknown platform"
  }
  case class InstallationError(t: Throwable) extends SetupImpossible {
    def explain = s"Download and installation failed: ${t.getMessage}"
  }

  private val logger = getLogger

  /**
   * Location of the success marker file.
   *
   * Detection of setups works by looking for the success marker file in the
   * path of the setup. If the root path of the setup is present, but not the
   * file, the setup is considered corrupted, for example because of a partial
   * download.
   */
  def successMarker(path: Path): Path =
    path.resolve(".success")

  /**
   * Download an official copy of Isabelle from the Internet and extract it
   * in the [[Platform#setupStorage:* designated path]] according to the
   * [[Platform platform]].
   */
  def install(platform: OfficialPlatform, version: Version): Either[SetupImpossible, Setup] = {
    Files.createDirectories(platform.setupStorage)
    val url = platform.url(version)
    logger.debug(s"Downloading setup $version from $url to ${platform.setupStorage}")
    Tar.download(url) match {
      case Success(stream) =>
        platform.withLock { () =>
          Tar.extractTo(platform.setupStorage, stream) match {
            case Success(path) =>
              Files.createFile(successMarker(path))
              stream.close()
              Right(Setup(path, platform, version))
            case Failure(ex) =>
              Left(InstallationError(ex))
          }
        }.getOrElse(Left(Busy(platform.lockFile)))
      case Failure(ex) =>
        logger.error(ex)(s"Failed to download $url")
        Left(InstallationError(ex))
    }
  }

  /**
   * Try to find an existing [[Setup setup]] in the
   * [[Platform#setupStorage:* designated path]] of the [[Platform platform]].
   */
  def detect(platform: Platform, version: Version): Either[NoSetup, Setup] = platform.withLock { () =>
    val path = platform.setupStorage(version)
    if (Files.isDirectory(path)) {
      if (Files.isRegularFile(successMarker(path)))
        Right(Setup(path, platform, version))
      else
        Left(Corrupted(path))
    }
    else
      Left(Absent)
  }.getOrElse(Left(Busy(platform.lockFile)))

  /**
   * The default setup: [[Platform.guess default platform]],
   * [[detect detect existing setup]],
   * [[install install if not existing]].
   */
  def default(version: Version): Either[SetupImpossible, Setup] =
    Platform.guess match {
      case None =>
        Left(UnknownPlatform)
      case Some(platform) =>
        detect(platform, version) match {
          case Right(install) =>     Right(install)
          case Left(Absent) =>       install(platform, version)
          case Left(Busy(p)) =>      Left(Busy(p))
          case Left(Corrupted(p)) => Left(Corrupted(p))
        }
    }

}

/**
 * A state-less, logic-less representation of a file system location containing
 * an Isabelle installation with a specified
 * [[info.hupel.isabelle.api.Version version]].
 *
 * It is recommended to obtain instances via the [[Setup$ companion object]].
 * No guarantees are made when constructing instances manually.
 *
 * ''Footnote''
 *
 * The file system location is called ''home'' throughout `libisabelle`.
 */
final case class Setup(home: Path, platform: Platform, version: Version) {

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]] using the
   * [[Resolver.Default default resolver]].
   */
  def makeEnvironment(configuration: Configuration)(implicit scheduler: Scheduler): Future[Environment] =
    makeEnvironment(Resolver.Default, platform.userStorage(version), configuration.components)

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]].
   *
   * If the [[Resolver resolver]] found an appropriate classpath, this method
   * also checks for matching [[info.hupel.isabelle.api.BuildInfo build info]].
   */
  def makeEnvironment(resolver: Resolver, user: Path, components: List[Path])(implicit scheduler: Scheduler): Future[Environment] =
    resolver.resolve(platform, version).map(Environment.instantiate(version, _, Environment.Context(home, user, components)))

}
