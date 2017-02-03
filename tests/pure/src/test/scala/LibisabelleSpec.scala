package info.hupel.isabelle.tests

import java.nio.file.Paths

import scala.concurrent._
import scala.math.BigInt

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.pure._

class LibisabelleSpec(val specs2Env: Env) extends Specification
  with FullSetup
  with IsabelleMatchers { def is = sequential ^ s2"""

  Basic protocol interaction

  An Isabelle session
    can be started             ${system must exist.awaitFor(duration)}
    supports term parsing      ${parseCheck must beSuccess(Option.empty[String]).awaitFor(duration)}
    can parse terms            ${parsed must beSome.awaitFor(duration)}
    can't parse wrong terms    ${parseFailed must beNone.awaitFor(duration)}
    handles missing operations ${missingOperation must beFailure.awaitFor(duration)}
    can load theories          ${loaded must beSuccess(()).awaitFor(duration)}
    handles operation errors   ${operationError must beFailure.awaitFor(duration)}
    can cancel requests        ${cancelled.failed must beAnInstanceOf[CancellationException].awaitFor(duration)}"""


  // Pure operations

  val thy = Theory.get("Pure")
  val ctxt = Context.initGlobal(thy)

  val parseCheck = system.flatMap(sys => Term.parse(ctxt)("TERM x").check(sys, "Protocol_Pure"))

  val parsed = system.flatMap(_.run(Expr.fromString[Prop](ctxt, "TERM x"), "Protocol_Pure"))
  val parseFailed = system.flatMap(_.run(Expr.fromString[Prop](ctxt, "TERM"), "Protocol_Pure"))


  val AbsentOperation = Operation.implicitly[Unit, Unit]("absent_operation")

  val missingOperation = system.flatMap(_.invoke(AbsentOperation)(()))


  // Loading auxiliary files

  val loaded = system.flatMap(_.invoke(Operation.UseThys)(List(resources.findTheory(Paths.get("tests/Sleepy.thy")).get)))

  val Sleepy = Operation.implicitly[BigInt, Unit]("sleepy")

  val operationError =
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
      res <- { val future = s.invoke(Sleepy)(1); future.cancel(); future }
    }
    yield ()

}
