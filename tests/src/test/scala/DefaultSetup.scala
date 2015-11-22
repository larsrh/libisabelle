package edu.tum.cs.isabelle.tests

import java.nio.file.Paths

import org.specs2.specification.core.Env

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.bootstrap._
import edu.tum.cs.isabelle.setup._

trait DefaultSetup {
  val specs2Env: Env

  lazy val version: String =
    Option(java.lang.System.getenv("ISABELLE_VERSION")).orElse(
      specs2Env.arguments.commandLine.value("isabelle.version")
    ).get

  lazy val platform: Platform = Setup.defaultPlatform.get
  lazy val setup: Setup = Setup.detectSetup(platform, Version(version)).get
  lazy val env: Environment = setup.makeEnvironment(Bootstrap.implementations).get
  lazy val config: Configuration = Configuration.fromPath(Paths.get("."), s"Protocol$version")
}
