package edu.tum.cs.isabelle.tests

import java.nio.file.Paths

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.bootstrap._
import edu.tum.cs.isabelle.setup._

trait DefaultSetup {
  lazy val version: String = java.lang.System.getenv("ISABELLE_VERSION")
  lazy val setup: Setup = Setup.detectSetup(Setup.defaultBasePath, Version(version)).get
  lazy val env: Environment = setup.makeEnvironment(Bootstrap.implementations).get
  lazy val config: env.Configuration = env.Configuration.fromPath(Paths.get("."), "Protocol")
}
