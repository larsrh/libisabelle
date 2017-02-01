package info.hupel.isabelle.examples.scala

import scala.concurrent._
import scala.concurrent.duration._

import monix.execution.Scheduler.Implicits.global

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object Hello_PIDE extends App {

  val setup = Setup.default(Version("2016")).right.get // yolo
  val resources = Resources.dumpIsabelleResources().right.get // yolo
  val config = resources.makeConfiguration(Nil, Nil, "Protocol")

  val transaction =
    for {
      env <- setup.makeEnvironment(config)
      sys <- System.create(env, config)
      response <- sys.invoke(Operation.Hello)("world")
      _ = println(response.unsafeGet)
      () <- sys.dispose
    } yield ()

  Await.result(transaction, Duration.Inf)

}
