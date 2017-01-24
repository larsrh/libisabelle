package info.hupel.isabelle.pure

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

  def untypedCertify: ml.Expr[Context => Cterm] =
    term.certify andThen ml.Expr.uncheckedLiteral[Option[Cterm] => Cterm]("the")

  def typedCertify: ml.Expr[Context => Cexpr[T]] =
    untypedCertify.coerce

  def evaluate(ctxt: ml.Expr[Context]): Program[Expr[T]] =
    term.evaluate(ctxt).toProg.map(Expr[T])

  def print: ml.Expr[Context => String] =
    term.print

}

object Expr {

  def fromTerm[T : Typeable](ctxt: ml.Expr[Context], term: Term): Program[Option[Expr[T]]] =
    term.constrain[T].check(ctxt).toProg.map(_.map(Expr[T]))

  def fromString[T : Typeable](ctxt: ml.Expr[Context], term: String): Program[Option[Expr[T]]] =
    Term.parse(ctxt)(term).toProg.flatMap {
      case None =>
        Program.pure(Option.empty[Expr[T]])
      case Some(term) =>
        fromTerm(ctxt, term)
    }

  def embed[T : Embeddable](ctxt: ml.Expr[Context], t: T): Program[Expr[T]] =
    Embeddable[T].embed(t).map(Expr[T](_)).flatMap(_.recheck(ctxt))

  def unsafeOfTerm[T](term: Term): Expr[T] = Expr(term)

  def untyped[T]: ml.Expr[Expr[T] => Term] =
    ml.Expr.uncheckedLiteral[Term => Term]("I").coerce

  val fromThm: ml.Expr[Thm => Expr[Prop]] =
    ml.Expr.uncheckedLiteral[Thm => Term]("Thm.prop_of").coerce

}

sealed abstract class Cexpr[T]

object Cexpr {

  def trivial: ml.Expr[Cexpr[Prop] => Thm] =
    untyped[Prop] andThen ml.Expr.uncheckedLiteral[Cterm => Thm]("Thm.trivial")

  def untyped[T]: ml.Expr[Cexpr[T] => Cterm] =
    ml.Expr.uncheckedLiteral[Cterm => Cterm]("I").coerce

  def uncertify[T]: ml.Expr[Cexpr[T] => Expr[T]] =
    ml.Expr.uncheckedLiteral[Cterm => Term]("Thm.term_of").coerce

  val fromThm: ml.Expr[Thm => Cexpr[Prop]] =
    ml.Expr.uncheckedLiteral[Thm => Cterm]("Thm.cprop_of").coerce

}
