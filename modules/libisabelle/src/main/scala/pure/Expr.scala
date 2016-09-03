package info.hupel.isabelle.pure

import scala.concurrent.{ExecutionContext, Future}

import cats.instances.future._
import cats.data.OptionT

import info.hupel.isabelle._

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
  def embed(t: T): Program[Term]
  def unembed(t: Term): Program[Option[T]]
}

object Embeddable {
  def apply[T](implicit T: Embeddable[T]) = T
}

final case class Expr[T] private[isabelle](val term: Term) {

  def |>[U](that: Expr[T => U]): Expr[U] =
    Expr(App(that.term, this.term))

  private def copy = this

  def recheck(ctxt: ml.Expr[Context])(implicit T: Typeable[T]): Program[Expr[T]] =
    Expr.fromTerm(ctxt, term).map(_.get)

  def unembed(implicit T: Embeddable[T]): Program[Option[T]] =
    T.unembed(term)

  def untypedCertify(ctxt: ml.Expr[Context]): ml.Expr[CTerm] =
    ml.Expr.uncheckedLiteral[Option[CTerm] => CTerm]("the")(term.certify(ctxt))

  def typedCertify(ctxt: ml.Expr[Context]): ml.Expr[CExpr[T]] =
    untypedCertify(ctxt).coerce[CExpr[T]]

  def evaluate(ctxt: ml.Expr[Context]): Program[Expr[T]] =
    term.evaluate(ctxt).toProg.map(Expr[T])

  def print(ctxt: ml.Expr[Context]): ml.Expr[String] =
    term.print(ctxt)

}

object Expr {

  def fromTerm[T : Typeable](ctxt: ml.Expr[Context], term: Term): Program[Option[Expr[T]]] =
    term.constrain[T].check(ctxt).toProg.map(_.map(Expr[T]))

  def fromString[T : Typeable](ctxt: ml.Expr[Context], term: String): Program[Option[Expr[T]]] =
    Term.parse(ctxt, term).toProg.flatMap {
      case None =>
        Program.pure(Option.empty[Expr[T]])
      case Some(term) =>
        fromTerm(ctxt, term)
    }

  def embed[T : Embeddable](ctxt: ml.Expr[Context], t: T): Program[Expr[T]] =
    Embeddable[T].embed(t).map(Expr[T](_)).flatMap(_.recheck(ctxt))

  def unsafeOfTerm[T](term: Term): Expr[T] = Expr(term)

  def untyped[T](expr: ml.Expr[Expr[T]]): ml.Expr[Term] =
    expr.coerce

  def fromThm(thm: ml.Expr[Thm]): ml.Expr[Expr[Prop]] =
    ml.Expr.uncheckedLiteral[Thm => Term]("Thm.prop_of")(thm).coerce

}

sealed abstract class CExpr[T]

object CExpr {

  def trivial(ce: ml.Expr[CExpr[Prop]]): ml.Expr[Thm] =
    ml.Expr.uncheckedLiteral[CTerm => Thm]("Thm.trivial")(untyped(ce))

  def untyped[T](ce: ml.Expr[CExpr[T]]): ml.Expr[CTerm] =
    ce.coerce

  def uncertify[T](ce: ml.Expr[CExpr[T]]): ml.Expr[Expr[T]] =
    ml.Expr.uncheckedLiteral[CTerm => Term]("Thm.term_of")(ce.coerce[CTerm]).coerce[Expr[T]]

  def fromThm(thm: ml.Expr[Thm]): ml.Expr[CExpr[Prop]] =
    ml.Expr.uncheckedLiteral[Thm => CTerm]("Thm.cprop_of")(thm).coerce

}
