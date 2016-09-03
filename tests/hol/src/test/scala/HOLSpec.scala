package info.hupel.isabelle.tests

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.ScalaCheck
import org.specs2.mutable.Specification
import org.specs2.scalacheck.Parameters
import org.specs2.specification.AfterAll
import org.specs2.specification.core.Env

import org.scalacheck._
import org.scalacheck.Prop.forAll

import org.log4s._

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.hol._
import info.hupel.isabelle.pure._

class HOLSpec(val specs2Env: Env) extends Specification
  with DefaultSetup
  with IsabelleMatchers
  with AfterAll
  with ScalaCheck {

  implicit val params = Parameters(minTestsOk = 20, maxSize = 10).verbose

  // Starting the system

  val config = resources.makeConfiguration(Nil, "HOL-Protocol")

  val system = isabelleEnv.flatMap(System.create(_, config))


  // HOL operations

  val thy = Theory.get("Protocol_Main")
  val ctxt = Context.initGlobal(thy)

  def run[A](prog: Program[A]): Future[A] =
    system.flatMap(_.run(prog, "Protocol_Main"))

  def propEmbedRecheck[A : Embeddable : Arbitrary] = forAll { (a: A) =>
    val prog =
      for {
        e1 <- Expr.embed(ctxt, a)
        e2 <- e1.recheck(ctxt)
      } yield e1.term == e2.term
    run(prog) must beTrue.awaitFor(duration)
  }

  def propEmbedUnembed[A : Embeddable : Arbitrary] = forAll { (a1: A) =>
    val prog = Expr.embed(ctxt, a1).flatMap(_.unembed)
    run(prog) must beSome(a1).awaitFor(duration)
  }


  // Teardown

  val logger = getLogger

  def afterAll() = {
    logger.info("Shutting down system ...")
    Await.result(system.flatMap(_.dispose), duration)
  }

  // Specification

  "An Isabelle/HOL session" >> {
    "can be started" >> {
      system must exist.awaitFor(duration)
    }
    "quasiquote" >> prop { (n: BigInt, b: Boolean) =>
      val prog = term"$n > $n --> $b".flatMap(Expr.fromString[Boolean](ctxt, _))
      run(prog) must beSome.awaitFor(duration)
    }
    "eval" >> prop { (b1: Boolean, b2: Boolean, b3: Boolean) =>
      val rScala = (b1 && b2) || b3
      val prog =
        for {
          e1 <- Expr.embed(ctxt, b1)
          e2 <- Expr.embed(ctxt, b2)
          e3 <- Expr.embed(ctxt, b3)
          e = (e1 ∧ e2) ∨ e3
          evaluated <- e.evaluate(ctxt)
          rIsabelle <- evaluated.unembed
        } yield rIsabelle
      run(prog) must beSome(rScala).awaitFor(duration)
    }
    "embed/recheck" >> {
      "BigInt" >> propEmbedRecheck[BigInt]
      "Boolean" >> propEmbedRecheck[Boolean]
      "List[BigInt]" >> propEmbedRecheck[List[BigInt]]
      "List[List[Boolean]]" >> propEmbedRecheck[List[List[Boolean]]]
    }
    "embed/unembed" >> {
      "BigInt" >> propEmbedUnembed[BigInt]
      "Boolean" >> propEmbedUnembed[Boolean]
      "List[BigInt]" >> propEmbedUnembed[List[BigInt]]
      "List[List[Boolean]]" >> propEmbedUnembed[List[List[Boolean]]]
    }
  }

}
