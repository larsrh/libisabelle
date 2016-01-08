package edu.tum.cs.isabelle.pure

import scala.concurrent.{ExecutionContext, Future}

import cats.std.future._
import cats.data.OptionT

import edu.tum.cs.isabelle._

trait Typeable[T] {
  def typ: Typ
}

object Typeable {
  def apply[T](implicit T: Typeable[T]) = T
  def typ[T](implicit T: Typeable[T]) = T.typ

  def make[T](typ0: Typ): Typeable[T] = new Typeable[T] {
    def typ: Typ = typ0
  }
}

case class Expr[T] private(term: Term)

object Expr {

  implicit def exprCodec[T : Typeable]: Codec[Expr[T]] =
    Codec[(Term, Typ)].ptransform(
      { case (term, typ) => if (typ == Typeable.typ[T]) Some(Expr[T](term)) else None },
      { case Expr(term) => (term, Typeable.typ[T]) }
    )

  val ParseTerm = Operation.implicitly[(String, String), Option[Term]]("parse_term")
  val CheckTerm = Operation.implicitly[(Term, Typ, String), Option[Term]]("check_term")

  def ofString[T : Typeable](thy: Theory, rawTerm: String)(implicit ec: ExecutionContext): OptionT[Future, Expr[T]] =
    OptionT(thy.system.invoke(ParseTerm)((rawTerm, thy.name)) map {
      case ProverResult.Failure(exn) => throw exn
      case ProverResult.Success(opt) => opt
    }).flatMap(ofTerm(thy, _))

  def ofTerm[T : Typeable](thy: Theory, term: Term)(implicit ec: ExecutionContext): OptionT[Future, Expr[T]] =
    OptionT(thy.system.invoke(CheckTerm)((term, Typeable[T].typ, thy.name)) map {
      case ProverResult.Failure(exn) => throw exn
      case ProverResult.Success(opt) => opt.map(Expr[T](_))
    })

}
