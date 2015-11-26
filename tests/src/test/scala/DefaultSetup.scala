package edu.tum.cs.isabelle.tests

import java.nio.file.Paths

import scala.concurrent.Future

import org.specs2.Specification
import org.specs2.specification.core.Env

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup._

trait DefaultSetup { self: Specification =>
  val specs2Env: Env
  implicit val ee = specs2Env.executionEnv

  lazy val version: Version =
    Option(java.lang.System.getenv("ISABELLE_VERSION")).orElse(
      specs2Env.arguments.commandLine.value("isabelle.version")
    ).map(Version.apply).get

  lazy val platform: Platform = Setup.defaultPlatform.get
  lazy val setup: Setup = Setup.detectSetup(platform, version).get
  lazy val env: Future[Environment] = setup.makeEnvironment
  lazy val config: Configuration = Configuration.fromPath(Paths.get("."), s"Protocol${version.identifier}")
}
