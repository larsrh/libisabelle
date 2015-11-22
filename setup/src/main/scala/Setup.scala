package edu.tum.cs.isabelle.setup

import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}

import edu.tum.cs.isabelle.Implementations
import edu.tum.cs.isabelle.api.{Environment, Version}

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
   * appropriate parameters.
   */
  def makeEnvironment(impls: Implementations): Option[Environment] =
    impls.makeEnvironment(home, version)
}
