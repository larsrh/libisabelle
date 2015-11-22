package edu.tum.cs.isabelle.app

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits

import edu.tum.cs.isabelle
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.bootstrap._
import edu.tum.cs.isabelle.setup.Setup

import acyclic.file

object Template {

  def guessVersion: Option[Version] =
    Option(System.getenv("ISABELLE_VERSION")).orElse(
      Option(System.getProperty("edu.tum.cs.isabelle.version"))
    ).map(Version)

  def getVersion(args: List[String]): (Option[Version], List[String]) = args match {
    case "--version" :: version :: rest => (Some(Version(version)), rest)
    case _ => (guessVersion, args)
  }

}

case class Bundle(env: Environment, version: Version, args: List[String]) {
  val config: Configuration =
    Configuration.fromPath(Paths.get("."), s"HOL-Protocol${version.identifier}")
  def system: Future[isabelle.System] =
    isabelle.System.create(env, config)
}

trait Template {

  def run(bundle: Bundle): Future[Unit]
  def duration: Duration

  final implicit def executionContext = Implicits.global

  final def main(args: Array[String]): Unit = Template.getVersion(args.toList) match {
    case (Some(version), args) =>
      val app = Setup.defaultSetup(version).flatMap { setup =>
        Bootstrap.implementations.makeEnvironment(setup.home, setup.version) match {
          case None =>
            sys.error("Unknown or unsupported Isabelle version")
          case Some(env) =>
            run(Bundle(env, version, args))
        }
      }

      Await.result(app, duration)
    case (None, _) =>
      sys.error("No Isabelle version specified")
  }

}
