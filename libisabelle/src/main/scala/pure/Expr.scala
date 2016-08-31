package info.hupel.isabelle.pure

import scala.concurrent.{ExecutionContext, Future}

import cats.instances.future._
import cats.data.OptionT

import info.hupel.isabelle._
import info.hupel.isabelle.ffi.MLExpr
import info.hupel.isabelle.ffi.types._

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
    make(typ[T] -->: typ[U])
}

trait Embeddable[T] extends Typeable[T] {
  def embed(thy: MLExpr[Theory], t: T): Program[Term]
  def unembed(thy: MLExpr[Theory], t: Term): Program[Option[T]]
}

object Embeddable {
  def apply[T](implicit T: Embeddable[T]) = T
}

final case class Expr[T] private[isabelle](term: Term) {
  def |>[U](that: Expr[T => U]): Expr[U] =
    Expr(App(that.term, this.term))

  private def copy = this

  def recheck(thy: MLExpr[Theory])(implicit T: Typeable[T]): Program[Expr[T]] =
    MLProg.unsafeExpr(MLExpr.the(MLExpr.checkTerm(MLExpr.initGlobal(thy), term.constrain(Typeable[T].typ)))).map(Expr[T])

  def unembed(thy: MLExpr[Theory])(implicit T: Embeddable[T]): Program[Option[T]] =
    T.unembed(thy, term)
}

object Expr {

  def ofString[T : Typeable](thy: MLExpr[Theory], term: String): Program[Option[Expr[T]]] = {
    val ctxt = MLExpr.initGlobal(thy)

    MLProg.unsafeExpr(MLExpr.parseTerm(ctxt, term)).flatMap {
      case None => MLProg.pure(Option.empty[Term])
      case Some(term) => MLProg.unsafeExpr(MLExpr.checkTerm(ctxt, term.constrain(Typeable[T].typ)))
    }.map(_.map(Expr[T](_)))
  }

  def ofTerm[T : Typeable](thy: MLExpr[Theory], term: Term): Program[Option[Expr[T]]] =
    MLProg.unsafeExpr(MLExpr.checkTerm(MLExpr.initGlobal(thy), term.constrain(Typeable[T].typ))).map(_.map(Expr[T]))

  def embed[T : Embeddable](thy: MLExpr[Theory], t: T): Program[Expr[T]] =
    Embeddable[T].embed(thy, t).map(Expr[T](_)).flatMap(_.recheck(thy))

  def unsafeOfTerm[T](term: Term): Expr[T] = Expr(term)

}
