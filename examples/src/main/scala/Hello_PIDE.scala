package edu.tum.cs.isabelle.examples.scala

import scala.concurrent._
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup._

object Hello_PIDE extends App {


  val transaction =
    for {
      setup <- Setup.defaultSetup(Version("2015")).toOption.get // yolo
      env <- setup.makeEnvironment
      resources = Resources.dumpIsabelleResources()
      config = resources.makeConfiguration(Nil, "Protocol")
      sys <- System.create(env, config)
      response <- sys.invoke(Operation.Hello)("world")
      _ = println(response.unsafeGet)
      () <- sys.dispose
    } yield ()

  Await.result(transaction, Duration.Inf)

}
