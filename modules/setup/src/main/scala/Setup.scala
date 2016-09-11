package info.hupel.isabelle.setup

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}
import scala.util._

import cats.data.Xor

import org.log4s._

import info.hupel.isabelle.api.{BuildInfo, Environment, Version}

/**
 * Detecting and creating [[Setup setups]].
 *
 * This object assumes that there is a ''base path'' in which all Isabelle
 * setups reside. Given a [[info.hupel.isabelle.api.Version version]], the
 * base path can either be searched for an existing setup, or an archive can
 * be downloaded from the Internet and extracted into the path.
 */
object Setup {

  sealed trait NoSetup
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

  /** Default platform: [[Platform.guess guessing]]. */
  def defaultPlatform: Option[OfficialPlatform] = Platform.guess

  /**
   * Default package name of PIDE jars.
   *
   * @see [[info.hupel.isabelle.api.Environment]]
   */
  val defaultPackageName: String = "info.hupel.isabelle.impl"

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
  def install(platform: OfficialPlatform, version: Version): Xor[SetupImpossible, Setup] = {
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
              Xor.right(Setup(path, platform, version, defaultPackageName))
            case Failure(ex) =>
              Xor.left(InstallationError(ex))
          }
        }.getOrElse(Xor.left(Busy(platform.lockFile)))
      case Failure(ex) =>
        logger.error(ex)(s"Failed to download $url")
        Xor.left(InstallationError(ex))
    }
  }

  /**
   * Try to find an existing [[Setup setup]] in the
   * [[Platform#setupStorage:* designated path]] of the [[Platform platform]].
   */
  def detectSetup(platform: Platform, version: Version): Xor[NoSetup, Setup] = platform.withLock { () =>
    val path = platform.setupStorage(version)
    if (Files.isDirectory(path)) {
      if (Files.isRegularFile(successMarker(path)))
        Xor.right(Setup(path, platform, version, defaultPackageName))
      else
        Xor.left(Corrupted(path))
    }
    else
      Xor.left(Absent)
  }.getOrElse(Xor.left(Busy(platform.lockFile)))

  /**
   * The default setup: [[defaultPlatform default platform]],
   * [[detectSetup detect existing setup]],
   * [[install install if not existing]].
   */
  def defaultSetup(version: Version): Xor[SetupImpossible, Setup] =
    defaultPlatform match {
      case None =>
        Xor.left(UnknownPlatform)
      case Some(platform) =>
        detectSetup(platform, version) match {
          case Xor.Right(install) =>     Xor.right(install)
          case Xor.Left(Absent) =>       install(platform, version)
          case Xor.Left(Busy(p)) =>      Xor.left(Busy(p))
          case Xor.Left(Corrupted(p)) => Xor.left(Corrupted(p))
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
final case class Setup(home: Path, platform: Platform, version: Version, packageName: String) {

  private val logger = getLogger

  private def instantiate(urls: List[URL])(implicit ec: ExecutionContext): Environment = {
    logger.debug(s"Instantiating setup with classpath ${urls.mkString(":")} ...")

    val classLoader = new URLClassLoader(urls.toArray, getClass.getClassLoader)
    val env = classLoader.loadClass(s"$packageName.Environment").asSubclass(classOf[Environment])

    val actualVersion = Environment.getVersion(env)
    if (actualVersion != version)
      sys.error(s"expected version $version, got version $actualVersion")

    val info = classLoader.loadClass(s"$packageName.BuildInfo").getDeclaredMethod("toString").invoke(null)
    if (BuildInfo.toString != info.toString)
      sys.error(s"build info does not match")

    val constructor = env.getDeclaredConstructor(classOf[Environment.Context])
    val context = Environment.Context(home, ec)
    constructor.setAccessible(true)
    constructor.newInstance(context)
  }

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]] using the
   * [[Resolver.Default default resolver]].
   */
  def makeEnvironment(implicit ec: ExecutionContext): Future[Environment] =
    makeEnvironment(Resolver.Default)

  /**
   * Prepares a fresh [[info.hupel.isabelle.api.Environment]].
   *
   * If the [[Resolver resolver]] found an appropriate classpath, this method
   * also checks for matching [[info.hupel.isabelle.api.BuildInfo build info]].
   */
  def makeEnvironment(resolver: Resolver)(implicit ec: ExecutionContext): Future[Environment] =
    resolver.resolve(platform, version).map(paths => instantiate(paths.map(_.toUri.toURL)))

}
