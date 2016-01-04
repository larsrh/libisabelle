package edu.tum.cs.isabelle.japi

import java.nio.file.Path

import scala.concurrent.{Await, Future}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration.Duration

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup._

object JSetup {

  def makeEnvironment(home: Path, platform: Platform, version: Version, timeout: Duration): Environment =
    Await.result(Setup(home, platform, version).makeEnvironment, timeout)

  def makeEnvironment(home: Path, platform: Platform, version: Version): Environment =
    makeEnvironment(home, platform, version, Duration.Inf)

}
