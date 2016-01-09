package edu.tum.cs.isabelle.tests

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.Specification
import org.specs2.specification.core.Env

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.pure._
import edu.tum.cs.isabelle.hol._

class LibisabelleSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Basic protocol interaction

  An Isabelle session
    can be started          ${system must exist.awaitFor(timeout)}
    can parse terms         ${parsed must beSome.awaitFor(timeout)}
    can't parse wrong terms ${parseFailed must beNone.awaitFor(timeout)}
    can load theories       ${loaded must beSuccess(()).awaitFor(timeout)}
    handles errors          ${error must beFailure.awaitFor(timeout)}
    can cancel requests     ${cancelled.failed must beAnInstanceOf[CancellationException].awaitFor(timeout)}
    can be torn down        ${teardown must exist.awaitFor(timeout)}"""


  def timeout = 30.seconds


  // Starting the system

  val system = env.flatMap(System.create(_, config))


  // Pure/HOL operations

  val theory = system.map(Theory(_, "Pure"))

  val parsed = theory.flatMap(Expr.ofString[Prop](_, "TERM x"))
  val parseFailed = theory.flatMap(Expr.ofString[Prop](_, "+"))


  // Loading auxiliary files

  val loaded = system.flatMap(_.invoke(Operation.UseThys)(List("tests/src/test/isabelle/Test")))

  val Sleepy = Operation.implicitly[BigInt, Unit]("sleepy")

  val error =
    for {
      s <- system
      _ <- loaded
      res <- s.invoke(Sleepy)(-1)
    }
    yield res

  val cancelled =
    for {
      s <- system
      _ <- loaded
      res <- { val future = s.cancellableInvoke(Sleepy)(1); future.cancel(); future.future }
    }
    yield ()



  val teardown =
    for {
      s <- system
      _ <- Future.sequence(List(parsed, parseFailed, error, cancelled.failed)) // barrier
      _ <- s.dispose
    }
    yield ()

}
