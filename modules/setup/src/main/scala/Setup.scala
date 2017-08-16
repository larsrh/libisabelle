package info.hupel.isabelle.setup

import java.nio.file.{Files, Path}

import scala.concurrent.Future
import scala.util._
import scala.util.control.NonFatal

import org.log4s._

import monix.execution.Scheduler

import info.hupel.isabelle._
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
  sealed trait NoSetup { def explain: String }

  /**
   * Common trait for a reason why a [[Setup setup]] could not be
   * [[install installed]].
   */
  sealed trait SetupImpossible { def explain: String }

  case object Absent extends NoSetup {
    def explain = "Setup is absent"
  }
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
  case class UnknownDevel(s: String) extends NoSetup with SetupImpossible {
    def explain = s"Unknown devel: $s"
  }
  case class DevelError(t: Throwable) extends NoSetup with SetupImpossible {
    def explain = s"Devel init/update failed: ${t.getMessage}"
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
   * Download a copy of Isabelle from the Internet and extract it
   * in the [[Platform#setupStorage:* designated path]] according to the
   * [[Platform platform]].
   */
  def install(platform: OfficialPlatform, version: Version): Either[SetupImpossible, Setup] = {
    val path = platform.setupStorage(version, false)
    Files.createDirectories(path)

    version match {
      case v: Version.Stable =>
        val url = platform.url(v)
        logger.debug(s"Downloading setup $v from $url to $path")

        Tar.download(url) match {
          case Success(stream) =>
            platform.withLock { () =>
              Tar.extractTo(path, stream) match {
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
      case Version.Devel(identifier) =>
        Devel.knownDevels.get(identifier) match {
          case Some(devel) =>
            logger.debug(s"Initializing devel $identifier in $path")
            try {
              devel.init(path)
              Right(Setup(path, platform, version))
            }
            catch {
              case NonFatal(ex) => Left(DevelError(ex))
            }
          case None =>
            Left(UnknownDevel(identifier))
        }
    }
  }

  /**
   * Try to find an existing [[Setup setup]] in the
   * [[Platform#setupStorage:* designated path]] of the [[Platform platform]].
   */
  def detect(platform: Platform, version: Version, updateIfDevel: Boolean): Either[NoSetup, Setup] = platform.withLock { () =>
    val path = platform.setupStorage(version, true)
    if (Files.isDirectory(path))
      version match {
        case _: Version.Stable =>
          if (Files.isRegularFile(successMarker(path)))
            Right(Setup(path, platform, version))
          else
            Left(Corrupted(path))
        case _: Version.Devel if !updateIfDevel =>
          Right(Setup(path, platform, version))
        case Version.Devel(identifier) =>
          Devel.knownDevels.get(identifier) match {
            case Some(devel) =>
              logger.debug(s"Updating devel $identifier in $path")
              try {
                devel.update(path)
                Right(Setup(path, platform, version))
              }
              catch {
                case NonFatal(ex) => Left(DevelError(ex))
              }
            case None =>
              Left(UnknownDevel(identifier))
          }
      }
    else
      Left(Absent)
  }.getOrElse(Left(Busy(platform.lockFile)))

  /**
   * The default setup: [[info.hupel.isabelle.Platform.guess default platform]],
   * [[detect detect existing setup]],
   * [[install install if not existing]].
   */
  def default(version: Version, updateIfDevel: Boolean): Either[SetupImpossible, Setup] =
    Platform.guess match {
      case None =>
        Left(UnknownPlatform)
      case Some(platform) =>
        detect(platform, version, updateIfDevel) match {
          case Right(install) =>           Right(install)
          case Left(Absent) =>             install(platform, version)
          case Left(e: SetupImpossible) => Left(e)
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

  private val logger = getLogger

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]] using the
   * [[Resolver.Default default resolver]].
   */
  def makeEnvironment(resources: Resources, options: List[OptionKey.Update])(implicit scheduler: Scheduler): Future[Environment] =
    makeEnvironment(Resolver.Default, platform.userStorage(version), List(resources.component), options)

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]].
   *
   * If the [[Resolver resolver]] found an appropriate classpath, this method
   * also checks for matching [[info.hupel.isabelle.api.BuildInfo build info]].
   */
  def makeEnvironment(resolver: Resolver, user: Path, components: List[Path], options: List[OptionKey.Update])(implicit scheduler: Scheduler): Future[Environment] = {
    val context = Environment.Context(home, user, components, options)
    def makeGenericEnvironment = GenericEnvironment(context, version, platform) match {
      case Right(env) => Future.successful(env)
      case Left(err) => Future.failed(new RuntimeException(err.explain))
    }

    version match {
      case v: Version.Devel => makeGenericEnvironment
      case v: Version.Stable =>
        resolver.resolve(platform, v).map(Environment.instantiate(version, _, context)).recoverWith { case ex =>
          logger.warn(ex)("Environment could not be instantiated, falling back to generic environment")
          makeGenericEnvironment
        }
    }
  }

}
