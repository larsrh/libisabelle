package edu.tum.cs.isabelle.setup

import java.io.File
import java.net.{URL, URLClassLoader}
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}
import scala.util._

import cats.data.Xor

import coursier._

import org.log4s._

import edu.tum.cs.isabelle.api.{BuildInfo, Environment, Version}

import acyclic.file

/**
 * Detecting and creating [[Setup setups]].
 *
 * This object assumes that there is a ''base path'' in which all Isabelle
 * setups reside. Given a [[edu.tum.cs.isabelle.api.Version version]], the
 * base path can either be searched for an existing setup, or an archive can
 * be downloaded from the Internet and extracted into the path.
 */
object Setup {

  sealed trait NoSetupReason
  sealed trait SetupImpossibleReason { def explain: String }
  case object Absent extends NoSetupReason
  case class Corrupted(path: Path) extends NoSetupReason with SetupImpossibleReason {
    def explain = s"Possibly corrupted setup detected at $path; try deleting that folder and running again"
  }
  case class Busy(path: Path) extends NoSetupReason with SetupImpossibleReason {
    def explain = s"File lock $path could not be acquired (busy)"
  }
  case object UnknownPlatform extends SetupImpossibleReason {
    def explain = "Impossible to download setup on unknown platform"
  }

  private val logger = getLogger

  /** Default platform: [[Platform.guess guessing]]. */
  def defaultPlatform: Option[OfficialPlatform] = Platform.guess

  /**
   * Default package name of PIDE jars.
   *
   * @see [[edu.tum.cs.isabelle.api.Environment]]
   */
  val defaultPackageName: String = "edu.tum.cs.isabelle.impl"

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

  def install(platform: OfficialPlatform, version: Version)(implicit ec: ExecutionContext): Future[Setup] = {
    Files.createDirectories(platform.setupStorage)
    val url = platform.url(version)
    logger.debug(s"Downloading setup $version from $url to ${platform.setupStorage}")
    Tar.download(url) match {
      case Success(stream) =>
        val future = platform.withAsyncLock { () =>
          Tar.extractTo(platform.setupStorage, stream).map { path =>
            Files.createFile(successMarker(path))
            Setup(path, platform, version, defaultPackageName)
          }
        }
        future.onComplete { _ => stream.close() }
        future
      case Failure(ex) =>
        logger.error(ex)(s"Failed to download $url")
        Future.failed(ex)
    }
  }

  def detectSetup(platform: Platform, version: Version): Xor[NoSetupReason, Setup] = platform.withLock { () =>
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

  def defaultSetup(version: Version)(implicit ec: ExecutionContext): Xor[SetupImpossibleReason, Future[Setup]] =
    defaultPlatform match {
      case None =>
        Xor.left(UnknownPlatform)
      case Some(platform) =>
        detectSetup(platform, version) match {
          case Xor.Right(install) =>     Xor.right(Future.successful(install))
          case Xor.Left(Absent) =>       Xor.right(install(platform, version))
          case Xor.Left(Busy(p)) =>      Xor.left(Busy(p))
          case Xor.Left(Corrupted(p)) => Xor.left(Corrupted(p))
        }
    }

  def fetchImplementation(platform: Platform, version: Version)(implicit ec: ExecutionContext): Future[List[Path]] = {
    val base = platform.versionedStorage

    val repositories = Seq(
      coursier.Cache.ivy2Local,
      MavenRepository("https://repo1.maven.org/maven2"),
      MavenRepository("https://oss.sonatype.org/content/repositories/releases")
    )

    val downloadLogger = new coursier.Cache.Logger {
      override def downloadingArtifact(url: String, file: File) =
        logger.debug(s"Downloading artifact from $url ...")
      override def downloadedArtifact(url: String, success: Boolean) = {
        val file = url.split('/').last
        if (success)
          logger.debug(s"Successfully downloaded $file")
        else
          logger.error(s"Failed to download $file")
      }
    }

    val fetch = Fetch.from(
      repositories,
      Cache.fetch(logger = Some(downloadLogger), cachePolicy = CachePolicy.LocalOnly),
      Cache.fetch(logger = Some(downloadLogger), cachePolicy = CachePolicy.FetchMissing)
    )

    def resolve(identifier: String) = {
      val dependency =
        Dependency(
          Module(BuildInfo.organization, s"pide-${identifier}_${BuildInfo.scalaBinaryVersion}"),
          BuildInfo.version
        )
      Resolution(Set(dependency)).process.run(fetch).toScalaFuture.map { res =>
        if (!res.isDone)
          sys.error("not converged")
        else if (!res.errors.isEmpty)
          sys.error(s"errors: ${res.errors}")
        else if (!res.conflicts.isEmpty)
          sys.error(s"conflicts: ${res.conflicts}")
        else
          res.artifacts.toSet
      }
    }

    def fetchArtifact(artifact: Artifact, cachePolicy: CachePolicy) =
      Cache.file(artifact, logger = Some(downloadLogger), cachePolicy = cachePolicy)

    platform.withAsyncLock { () =>
      for {
        i <- resolve("interface")
        v <- resolve(version.identifier)
        artifacts = v -- i
        res <- Future.traverse(artifacts.toList)(artifact =>
          (fetchArtifact(artifact, CachePolicy.LocalOnly)
             orElse fetchArtifact(artifact, CachePolicy.FetchMissing))
            .run.toScalaFuture
        )
      }
      yield
        res.map(_.fold(err => sys.error(err.toString), _.toPath))
    }
  }

}

/**
 * A state-less, logic-less representation of a file system location containing
 * an Isabelle installation with a specified
 * [[edu.tum.cs.isabelle.api.Version version]].
 *
 * It is recommended to obtain instances via the [[Setup$ companion object]].
 * No guarantees are made when constructing instances manually.
 *
 * ''Footnote''
 *
 * The file system location is called ''home'' throughout `libisabelle`.
 */
final case class Setup(home: Path, platform: Platform, version: Version, packageName: String) {

  private def instantiate(urls: List[URL]): Environment = {
    val classLoader = new URLClassLoader(urls.toArray, getClass.getClassLoader)
    val env = classLoader.loadClass(s"$packageName.Environment").asSubclass(classOf[Environment])

    val actualVersion = Environment.getVersion(env)
    if (actualVersion != version)
      sys.error(s"expected version $version, got version $actualVersion")

    val info = classLoader.loadClass(s"$packageName.BuildInfo").getDeclaredMethod("toString").invoke(null)
    if (BuildInfo.toString != info.toString)
      sys.error(s"build info does not match")

    val constructor = env.getDeclaredConstructor(classOf[Path])
    constructor.setAccessible(true)
    constructor.newInstance(home)
  }

  /**
   * Convenience method aliasing
   * [[edu.tum.cs.isabelle.Implementations#makeEnvironment]] with the
   * appropriate parameters. It calls [[Setup.fetchImplementation]] to download
   * the required classpath.
   */
  def makeEnvironment(implicit ec: ExecutionContext): Future[Environment] =
    Setup.fetchImplementation(platform, version).map(paths => instantiate(paths.map(_.toUri.toURL)))

}
