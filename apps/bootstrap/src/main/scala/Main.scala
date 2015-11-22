package edu.tum.cs.isabelle.app.bootstrap

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration._

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.app._

object Main extends Template {

  def duration = Duration.Inf

  def run(bundle: Bundle) = {
    val config = Configuration.fromPath(Paths.get("."), s"Protocol${bundle.version.identifier}")
    val built = System.build(bundle.env, config)
    if (!built)
      sys.error("build error")
    Future.successful { () }
  }

}
