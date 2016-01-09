package edu.tum.cs.isabelle.pure

import scala.concurrent.{ExecutionContext, Future}

import cats.std.future._
import cats.data.OptionT

import edu.tum.cs.isabelle._

import acyclic.file

trait Typeable[T] {
  def typ: Typ
}

object Typeable {
  def apply[T](implicit T: Typeable[T]) = T
  def typ[T](implicit T: Typeable[T]) = T.typ

  def make[T](typ0: Typ): Typeable[T] = new Typeable[T] {
    def typ: Typ = typ0
  }

  implicit def funTypeable[T : Typeable, U : Typeable]: Typeable[T => U] =
    make(Type("fun", List(typ[T], typ[U])))
}

trait Embeddable[T] extends Typeable[T] {
  def embed(thy: Theory, t: T)(implicit ec: ExecutionContext): Future[Term]
}

object Embeddable {
  def apply[T](implicit T: Embeddable[T]) = T
}

case class Expr[T] private(term: Term) {
  def |>[U](that: Expr[T => U]): Expr[U] =
    Expr(App(that.term, this.term))
}

object Expr {

  implicit def exprCodec[T : Typeable]: Codec[Expr[T]] =
    Codec[(Term, Typ)].ptransform(
      { case (term, typ) => if (typ == Typeable.typ[T]) Some(Expr[T](term)) else None },
      { case Expr(term) => (term, Typeable.typ[T]) }
    )

  private val ReadTerm = Operation.implicitly[(String, Typ, String), Option[Term]]("read_term")
  private val CheckTerm = Operation.implicitly[(Term, Typ, String), Option[Term]]("check_term")

  private def fromProver[T](result: ProverResult[Option[Term]]): Option[Expr[T]] =
    result.unsafeGet.map(Expr[T](_))

  def ofString[T : Typeable](thy: Theory, rawTerm: String)(implicit ec: ExecutionContext): Future[Option[Expr[T]]] =
    thy.system.invoke(ReadTerm)((rawTerm, Typeable[T].typ, thy.name)).map(fromProver)

  def ofTerm[T : Typeable](thy: Theory, term: Term)(implicit ec: ExecutionContext): Future[Option[Expr[T]]] =
    thy.system.invoke(CheckTerm)((term, Typeable[T].typ, thy.name)).map(fromProver)

  def embed[T : Embeddable](thy: Theory, t: T)(implicit ec: ExecutionContext): Future[Expr[T]] =
    Embeddable[T].embed(thy, t).map(Expr[T](_))

}
