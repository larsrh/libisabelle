package edu.tum.cs.isabelle.setup

import java.nio.file.{Files, Path, Paths}

import scala.concurrent._

object Setup {

  def defaultVersion =
    Version.latest

  def defaultBasePath =
    Paths.get("contrib")

  def defaultPlatform =
    Platform.guess

  private def defaultInstallTo(path: Path)(implicit ec: ExecutionContext) =
    defaultPlatform.flatMap(defaultVersion.url) match {
      case None =>
        sys.error("couldn't determine URL")
      case Some(url) =>
        val stream = Tar.download(url)
        Tar.extractTo(path, stream).map(Environment(_, defaultVersion))
    }

  def defaultSetup(implicit ec: ExecutionContext): Future[Environment] =
    defaultVersion detectEnvironment defaultBasePath match {
      case Some(install) =>
        Future.successful(install)
      case None =>
        defaultInstallTo(defaultBasePath)
    }

  def temporarySetup(implicit ec: ExecutionContext): Future[Environment] =
    defaultInstallTo(Files.createTempDirectory("libisabelle").toRealPath())

  def guessEnvironment(path: Path): Option[Environment] =
    Version.all.flatMap(_.detectEnvironment(path)).headOption

}

object LocalSetup extends App {

  import scala.concurrent.ExecutionContext.Implicits.global
  println("Downloading and untarring latest supported Isabelle")
  val path = Await.result(Setup.defaultSetup, duration.Duration.Inf)
  println(s"Successfully untarred latest Isabelle into $path")

}
