package edu.tum.cs.isabelle.setup

import java.nio.file.{Files, Path, Paths}

import scala.concurrent.{Future, ExecutionContext}

import edu.tum.cs.isabelle.api.{Environment, Version}

object Setup {

  def defaultVersion =
    Version.latest

  def defaultBasePath =
    Paths.get("contrib")

  def defaultPlatform =
    Platform.guess

  private def defaultInstallTo(path: Path)(implicit ec: ExecutionContext) =
    defaultPlatform.flatMap(_.url(defaultVersion)) match {
      case None =>
        sys.error("couldn't determine URL")
      case Some(url) =>
        val stream = Tar.download(url)
        Tar.extractTo(path, stream).map(Setup(_, defaultVersion))
    }

  def defaultSetup(implicit ec: ExecutionContext): Future[Setup] =
    detectSetup(defaultVersion, defaultBasePath) match {
      case Some(install) =>
        Future.successful(install)
      case None =>
        defaultInstallTo(defaultBasePath)
    }

  def temporarySetup(implicit ec: ExecutionContext): Future[Setup] =
    defaultInstallTo(Files.createTempDirectory("libisabelle").toRealPath())

  def detectSetup(version: Version, base: Path): Option[Setup] = {
    val path = base resolve s"Isabelle${version.identifier}"
    if (Files.isDirectory(path))
      Some(Setup(path, version))
    else
      None
  }

}

case class Setup(home: Path, version: Version)
