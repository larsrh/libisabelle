package info.hupel.isabelle.japi

import scala.concurrent.Await
import scala.concurrent.duration.Duration

import monix.execution.Scheduler.Implicits.global

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object JSetup {

  def defaultSetup(version: Version.Stable, timeout: Duration): Setup =
    Setup.default(version) match {
      case Left(reason) => sys.error(reason.explain)
      case Right(setup) => setup
    }

  def defaultSetup(version: Version.Stable): Setup =
    defaultSetup(version, Duration.Inf)

  def makeEnvironment(setup: Setup, resources: JResources, timeout: Duration): Environment =
    Await.result(setup.makeEnvironment(resources.getResources()), timeout)

  def makeEnvironment(setup: Setup, resources: JResources): Environment =
    makeEnvironment(setup, resources, Duration.Inf)

}
