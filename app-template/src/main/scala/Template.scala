package edu.tum.cs.isabelle.app

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup.Setup

import acyclic.file

object Template {

  /**
   * Guesses an Isabelle [[edu.tum.cs.isabelle.api.Version version]] from
   * the system environment.
   *
   * The following is tried, in order:
   * - the environment variable `ISABELLE_VERSION`
   * - Java system property `edu.tum.cs.isabelle.version`
   */
  def guessVersion: Option[Version] =
    Option(System.getenv("ISABELLE_VERSION")).orElse(
      Option(System.getProperty("edu.tum.cs.isabelle.version"))
    ).map(Version)

  /**
   * Tries to read an Isabelle [[edu.tum.cs.isabelle.api.Version version]] from
   * the command line, or else [[guessVersion guesses an Isabelle version]]
   * from the environment.
   *
   * If there are at least two command line arguments and the first is
   * `--version`, the second is assumed to be the version identifier.
   */
  def getVersion(args: List[String]): (Option[Version], List[String]) = args match {
    case "--version" :: version :: rest => (Some(Version(version)), rest)
    case _ => (guessVersion, args)
  }

}

/**
 * Simple bundle of an Isabelle [[edu.tum.cs.isabelle.api.Version version]], a
 * matching [[edu.tum.cs.isabelle.api.Environment environment]] and a list of
 * command line arguments.
 */
final case class Bundle(env: Environment, version: Version, args: List[String])

/**
 * Convenience trait for providing standalone `libisabelle`-powered
 * applications.
 *
 * Provides a default implementation of `main` which attempts to guess the
 * Isabelle [[edu.tum.cs.isabelle.api.Version version]] (see
 * [[Template.getVersion]] for details).
 *
 * Implementations provide an asynchronous [[run]] function and a [[duration]]
 * to block for completion.
 */
trait Template {

  /**
   * Execute an asynchronous action, optionally using the
   * [[executionContext provided `ExecutionContext`]].
   */
  def run(bundle: Bundle): Future[Unit]

  /** Duration to block for the completion of [[run]]. */
  def duration: Duration

  final implicit def executionContext: ExecutionContext = Implicits.global

  final def main(args: Array[String]): Unit = Template.getVersion(args.toList) match {
    case (Some(version), args) =>
      val app = Setup.defaultSetup(version).flatMap(_.makeEnvironment).flatMap { env =>
        run(Bundle(env, version, args))
      }

      Await.result(app, duration)
    case (None, _) =>
      sys.error("No Isabelle version specified")
  }

}
