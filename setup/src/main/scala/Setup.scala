package edu.tum.cs.isabelle.setup

import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}

import edu.tum.cs.isabelle.Implementations
import edu.tum.cs.isabelle.api.{Environment, Version}

object Setup {

  def defaultBasePath =
    Paths.get("contrib")

  def defaultPlatform =
    Platform.guess

  private def defaultInstallTo(path: Path, version: Version)(implicit ec: ExecutionContext) =
    defaultPlatform.flatMap(_.url(version)) match {
      case None =>
        sys.error("couldn't determine URL")
      case Some(url) =>
        val stream = Tar.download(url)
        Tar.extractTo(path, stream).map(Setup(_, version))
    }

  def temporarySetup(version: Version)(implicit ec: ExecutionContext): Future[Setup] =
    defaultInstallTo(Files.createTempDirectory("libisabelle").toRealPath(), version)

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
        defaultInstallTo(defaultBasePath, version)
    }


}

case class Setup(home: Path, version: Version) {
  def makeEnvironment(impls: Implementations): Option[Environment] =
    impls.makeEnvironment(home, version)
}
