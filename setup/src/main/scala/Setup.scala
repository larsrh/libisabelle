package edu.tum.cs.isabelle.setup

import java.io.File
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}

import coursier._

import edu.tum.cs.isabelle.Implementations
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

  /** Default platform: [[Platform.guess guessing]]. */
  def defaultPlatform: Option[Platform] =
    Platform.guess

  // FIXME this whole thing needs proper error handling

  def installTo(platform: Platform, version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    platform.url(version) match {
      case None =>
        sys.error("couldn't determine URL")
      case Some(url) =>
        val stream = Tar.download(url)
        Tar.extractTo(platform.localStorage, stream).map(Setup(_, platform, version))
    }

  def detectSetup(platform: Platform, version: Version): Option[Setup] = {
    val path = platform.localStorage.resolve(s"Isabelle${version.identifier}")
    if (Files.isDirectory(path))
      Some(Setup(path, platform, version))
    else
      None
  }

  def defaultSetup(version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    defaultPlatform match {
      case None =>
        sys.error("couldn't determine platform")
      case Some(platform) =>
        detectSetup(platform, version) match {
          case Some(install) =>
            Future.successful(install)
          case None =>
            installTo(platform, version)
        }
    }

  def fetchImplementation(platform: Platform, version: Version)(implicit ec: ExecutionContext): Future[List[Path]] = {
    val repositories = Seq(Repository.ivy2Local, Repository.mavenCentral, Repository.sonatypeReleases)

    val files = coursier.Files(
      Seq("https://" -> platform.localStorage.resolve("cache").toFile),
      () => sys.error("impossible")
    )

    val cachePolicy = Repository.CachePolicy.Default

    def resolve(identifier: String) = {
      val dependency =
        Dependency(
          Module(BuildInfo.organization, s"pide-${identifier}_${BuildInfo.scalaBinaryVersion}"),
          BuildInfo.version
        )
      Resolution(Set(dependency)).process.run(repositories).toScalaFuture.map { res =>
        if (!res.isDone)
          sys.error("not converged")
        else if (!res.errors.isEmpty)
          sys.error(s"errors: ${res.errors}")
        else
          res.artifacts.toSet
      }
    }

    val pideInterface = resolve("interface")
    val pideVersion = resolve(version.identifier)

    for {
      i <- pideInterface
      v <- pideVersion
      artifacts = v -- i
      res <- Future.traverse(artifacts.toList)(files.file(_, cachePolicy).run.toScalaFuture)
    }
    yield
      res.map(_.fold(sys.error, _.toPath))
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
case class Setup(home: Path, platform: Platform, version: Version) {

  /**
   * Convenience method aliasing
   * [[edu.tum.cs.isabelle.Implementations#makeEnvironment]] with the
   * appropriate parameters. It calls [[Setup.fetchImplementation]] to download
   * the required classpath.
   */
  def makeEnvironment(implicit ec: ExecutionContext): Future[Environment] =
    Setup.fetchImplementation(platform, version).map { paths =>
      val entry = Implementations.Entry(paths.map(_.toUri.toURL), "edu.tum.cs.isabelle.impl")
      Implementations.empty.add(entry).get.makeEnvironment(home, version).get
    }

}
