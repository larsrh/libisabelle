package info.hupel.isabelle

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigInt

import cats.instances.option._
import cats.instances.list._
import cats.syntax.traverse._

import info.hupel.isabelle._
import info.hupel.isabelle.ffi.MLExpr
import info.hupel.isabelle.ffi.types._
import info.hupel.isabelle.pure._

package hol {
  private class ListTypeable[T : Typeable] extends Typeable[List[T]] {
    def typ: Typ = Type("List.list", List(Typeable.typ[T]))
  }

  private[isabelle] trait LowPriorityImplicits {
    implicit def listTypeable[T : Typeable]: Typeable[List[T]] = new ListTypeable[T]
  }
}

package object hol extends LowPriorityImplicits {

  private val MkInt = Operation.implicitly[BigInt, Term]("mk_int")
  private val MkList = Operation.implicitly[(Typ, List[Term]), Term]("mk_list")

  private val DestInt = Operation.implicitly[Term, Option[BigInt]]("dest_int")
  private val DestList = Operation.implicitly[Term, Option[List[Term]]]("dest_list")

  implicit def bigIntTypeable: Embeddable[BigInt] = new Embeddable[BigInt] {
    def typ = HOLogic.intT
    def embed(t: BigInt) =
      MLProg.operation(MkInt, t)
    def unembed(t: Term) =
      MLProg.operation(DestInt, t)
  }

  implicit def boolTypeable: Embeddable[Boolean] = new Embeddable[Boolean] {
    def typ = HOLogic.boolT
    def embed(t: Boolean) = MLProg.pure {
      t match {
        case true => HOLogic.True
        case false => HOLogic.False
      }
    }
    def unembed(t: Term) = MLProg.pure {
      t match {
        case HOLogic.True => Some(true)
        case HOLogic.False => Some(false)
        case _ => None
      }
    }
  }

  implicit def listEmbeddable[T : Embeddable]: Embeddable[List[T]] = new ListTypeable[T] with Embeddable[List[T]] {
    def embed(ts: List[T]) =
      ts.traverse(Embeddable[T].embed) flatMap { ts =>
        MLProg.operation(MkList, ((Typeable.typ[T], ts)))
      }

    def unembed(t: Term) =
      MLProg.operation(DestList, t) flatMap {
        case None => MLProg.pure(None)
        case Some(ts) => ts.traverse(Embeddable[T].unembed).map(_.sequence)
      }
  }

  implicit class BoolExprOps(t: Expr[Boolean]) {
    def ∧(u: Expr[Boolean]): Expr[Boolean] =
      Expr(HOLogic.conj $ t.term $ u.term)

    def &(u: Expr[Boolean]): Expr[Boolean] = t ∧ u

    def ∨(u: Expr[Boolean]): Expr[Boolean] =
      Expr(HOLogic.disj $ t.term $ u.term)

    def |(u: Expr[Boolean]): Expr[Boolean] = t ∨ u

    def →(u: Expr[Boolean]): Expr[Boolean] =
      Expr(HOLogic.imp $ t.term $ u.term)

    def -->(u: Expr[Boolean]): Expr[Boolean] = t → u
  }

  implicit class HOLExprOps[A](t: Expr[A]) {
    def ≡(u: Expr[A])(implicit A: Typeable[A]): Expr[Boolean] =
      Expr(HOLogic.equ(A.typ) $ t.term $ u.term)
  }

}
