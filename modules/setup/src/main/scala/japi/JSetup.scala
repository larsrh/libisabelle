package info.hupel.isabelle.japi

import java.nio.file.Path

import scala.concurrent.{Await, Future}
import scala.concurrent.duration.Duration

import monix.execution.Scheduler.Implicits.global

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object JSetup {

  def defaultSetup(version: Version, timeout: Duration): Setup =
    Setup.default(version) match {
      case Left(reason) => sys.error(reason.explain)
      case Right(setup) => setup
    }

  def defaultSetup(version: Version): Setup =
    defaultSetup(version, Duration.Inf)

  def makeEnvironment(setup: Setup, timeout: Duration): Environment =
    Await.result(setup.makeEnvironment, timeout)

  def makeEnvironment(setup: Setup): Environment =
    makeEnvironment(setup, Duration.Inf)

}
