package info.hupel.isabelle.tests

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.{ScalaCheck, Specification}
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
  with ScalaCheck { def is = s2"""

  Isabelle/HOL operations

  An Isabelle/HOL session
    can be started               ${system must exist.awaitFor(duration)}

  Embedding and rechecking terms
    of type BigInt               ${propEmbedRecheck[BigInt]}
    of type Boolean              ${propEmbedRecheck[Boolean]}
    of type List[BigInt]         ${propEmbedRecheck[List[BigInt]]}
    of type List[List[Boolean]]  ${propEmbedRecheck[List[List[Boolean]]]}

  Embedding and unembedding terms
    of type BigInt               ${propEmbedUnembed[BigInt]}
    of type Boolean              ${propEmbedUnembed[Boolean]}
    of type List[BigInt]         ${propEmbedUnembed[List[BigInt]]}
    of type List[List[Boolean]]  ${propEmbedUnembed[List[List[Boolean]]]}

  Quasiquotes                    ${quasiquotes}
  Term evaluation                ${eval}
  Peeking                        ${peeking}"""

  implicit val params = Parameters(minTestsOk = 20, maxSize = 10).verbose
  override def session = "HOL-Protocol"


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

  val quasiquotes = prop { (n: BigInt, b: Boolean) =>
    val prog = term"$n > $n --> ($b & ${HOLogic.True})".flatMap(Expr.fromString[Boolean](ctxt, _))
    run(prog) must beSome.awaitFor(duration)
  }

  val eval = prop { (b1: Boolean, b2: Boolean, b3: Boolean) =>
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

  val peeking = prop { (n: BigInt, m: BigInt) =>
    val rScala = n + m

    val prog =
      for {
        str <- term"$n + $m"
        expr <- Expr.fromString[BigInt](ctxt, str).map(_.get)
        terms <-
          Cterm.eval(ctxt)(expr.untypedCertify(ctxt)).peek(Term.fromThm)(new ml.Scoped[Thm, Term, (Term, Term)] {
            def apply(scope: ml.LocalScope)(term: Term, ref: scope.Expr[Thm]) = {
              import scope._
              localize(Term.fromThm)(ref).toProg.map((term, _))
            }
          })
      } yield terms

    run(prog) must beLike[(Term, Term)] { case (t1, t2) =>
      t1 must be_===(t2)
    }.awaitFor(duration)
  }


  // Teardown

  val logger = getLogger

  def afterAll() = {
    logger.info("Shutting down system ...")
    Await.result(system.flatMap(_.dispose), duration)
  }

}
