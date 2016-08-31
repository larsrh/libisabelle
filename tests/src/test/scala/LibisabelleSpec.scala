package info.hupel.isabelle.tests

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.ffi.types._
import info.hupel.isabelle.hol._
import info.hupel.isabelle.pure._

class LibisabelleSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Basic protocol interaction

  An Isabelle session
    can be started          ${system must exist.awaitFor(timeout)}
    supports term parsing   ${parseCheck must beSuccess(Option.empty[String]).awaitFor(timeout)}
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

  val thy = Theory.get("Pure")
  val ctxt = Context.initGlobal(thy)

  val parseCheck = system.flatMap(sys => Term.parse(ctxt, "TERM x").check(sys, "Protocol_Pure"))

  val parsed = system.flatMap(_.run(Expr.fromString[Prop](ctxt, "TERM x"), "Protocol_Pure"))
  val parseFailed = system.flatMap(_.run(Expr.fromString[Prop](ctxt, "TERM"), "Protocol_Pure"))


  // Loading auxiliary files

  val loaded = system.flatMap(_.invoke(Operation.UseThys)(List(resources.findTheory(Paths.get("tests/Sleepy.thy")).get)))

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
      res <- { val future = s.invoke(Sleepy)(1); future.cancel(); future }
    }
    yield ()



  val teardown =
    for {
      s <- system
      _ <- Future.sequence(List(parsed, parseFailed, parseCheck, error, cancelled.failed)) // barrier
      _ <- s.dispose
    }
    yield ()

}
