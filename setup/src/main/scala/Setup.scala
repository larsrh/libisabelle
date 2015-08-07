package edu.tum.cs.isabelle.setup

import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}

import edu.tum.cs.isabelle.Implementations
import edu.tum.cs.isabelle.api.{Environment, Version}

/**
 * Detecting and creating [[Setup setups]].
 *
 * This object assumes that there is a ''base path'' in which all Isabelle
 * setups reside. Given a [[edu.tum.cs.isabelle.api.Version version]], the
 * base path can either be searched for an existing setup, or an archive can
 * be downloaded from the Internet and extracted into the path.
 */
object Setup {

  /** Default base path: `contrib` in the current working directory. */
  def defaultBasePath =
    Paths.get("contrib")

  /** Default platform: [[Platform.guess guessing]]. */
  def defaultPlatform =
    Platform.guess

  // FIXME return type?! Option[Future[Setup]]?
  def installTo(path: Path, version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    defaultPlatform.flatMap(_.url(version)) match {
      case None =>
        sys.error("couldn't determine URL")
      case Some(url) =>
        val stream = Tar.download(url)
        Tar.extractTo(path, stream).map(Setup(_, version))
    }

  def temporarySetup(version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    installTo(Files.createTempDirectory("libisabelle").toRealPath(), version)

  def detectSetup(base: Path, version: Version): Option[Setup] = {
    val path = base resolve s"Isabelle${version.identifier}"
    if (Files.isDirectory(path))
      Some(Setup(path, version))
    else
      None
  }

  def defaultSetup(version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    detectSetup(defaultBasePath, version) match {
      case Some(install) =>
        Future.successful(install)
      case None =>
        installTo(defaultBasePath, version)
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
case class Setup(home: Path, version: Version) {
  /**
   * Convenience method aliasing
   * [[edu.tum.cs.isabelle.Implementations#makeEnvironment]] with the
   * appropriate parameters.
   */
  def makeEnvironment(impls: Implementations): Option[Environment] =
    impls.makeEnvironment(home, version)
}
