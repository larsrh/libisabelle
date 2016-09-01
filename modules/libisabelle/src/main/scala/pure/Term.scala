package info.hupel.isabelle.pure

import scala.math.BigInt

import info.hupel.isabelle.Codec
import info.hupel.isabelle.api._
import info.hupel.isabelle.ffi.MLExpr
import info.hupel.isabelle.ffi.types._

object Typ {
  implicit lazy val typCodec: Codec[Typ] = new Codec.Variant[Typ]("typ") {
    val mlType = "typ"

    lazy val typType = Codec[(String, List[Typ])]
    val typTFree = Codec[(String, Sort)]
    val typTVar = Codec[(Indexname, Sort)]

    def enc(typ: Typ) = typ match {
      case Type(name, args)  => (0, typType.encode((name, args)))
      case TFree(name, sort) => (1, typTFree.encode((name, sort)))
      case TVar(iname, sort) => (2, typTVar.encode((iname, sort)))
    }

    def dec(idx: Int) = idx match {
      case 0 => Some(tree => typType.decode(tree).right.map  { case (name, args) => Type(name, args) })
      case 1 => Some(tree => typTFree.decode(tree).right.map { case (name, sort) => TFree(name, sort) })
      case 2 => Some(tree => typTVar.decode(tree).right.map  { case (iname, sort) => TVar(iname, sort) })
      case _ => None
    }
  }

  val dummyT: Typ = Type("dummy", Nil)
  def funT(t: Typ, u: Typ): Typ = Type("fun", List(t, u))
}

sealed abstract class Typ {
  def -->:(that: Typ) = Typ.funT(that, this)
  def --->:(thats: List[Typ]) = thats.foldRight(this)(_ -->: _)
}

final case class Type(name: String, args: List[Typ] = Nil) extends Typ
final case class TFree(name: String, sort: Sort) extends Typ
final case class TVar(name: Indexname, sort: Sort) extends Typ

object Term {
  implicit lazy val termCodec: Codec[Term] = new Codec.Variant[Term]("term") {
    val mlType = "term"

    val termConst = Codec[(String, Typ)]
    val termFree = Codec[(String, Typ)]
    val termVar = Codec[(Indexname, Typ)]
    val termBound = Codec[BigInt]
    lazy val termAbs = Codec[(String, Typ, Term)]
    lazy val termApp = Codec[(Term, Term)]

    def enc(term: Term) = term match {
      case Const(name, typ)     => (0, termConst.encode((name, typ)))
      case Free(name, typ)      => (1, termFree.encode((name, typ)))
      case Var(iname, typ)      => (2, termVar.encode((iname, typ)))
      case Bound(idx)           => (3, termBound.encode(idx))
      case Abs(name, typ, body) => (4, termAbs.encode((name, typ, body)))
      case App(f, x)            => (5, termApp.encode((f, x)))
    }

    def dec(idx: Int) = idx match {
      case 0 => Some(tree => termConst.decode(tree).right.map  { case (name, typ) => Const(name, typ) })
      case 1 => Some(tree => termFree.decode(tree).right.map   { case (name, typ) => Free(name, typ) })
      case 2 => Some(tree => termVar.decode(tree).right.map    { case (iname, typ) => Var(iname, typ) })
      case 3 => Some(tree => termBound.decode(tree).right.map  { idx => Bound(idx) })
      case 4 => Some(tree => termAbs.decode(tree).right.map    { case (name, typ, body) => Abs(name, typ, body) })
      case 5 => Some(tree => termApp.decode(tree).right.map    { case (f, x) => App(f, x) })
      case _ => None
    }
  }


  def parse(ctxt: MLExpr[Context], term: String): MLExpr[Option[Term]] =
    MLExpr.uncheckedLiteral[Context => String => Term]("Syntax.parse_term")(ctxt).liftTry(term)

  def read(ctxt: MLExpr[Context], term: String): MLExpr[Option[Term]] =
    MLExpr.uncheckedLiteral[Context => String => Term]("Syntax.read_term")(ctxt).liftTry(term)
}

sealed abstract class Term {

  def $(that: Term): Term = App(this, that)

  def constrain(typ: Typ): Term = typ match {
    case Typ.dummyT => this
    case _ => Const ("_type_constraint_", typ -->: typ) $ this
  }

  def constrain[T : Typeable]: Term = constrain(Typeable[T].typ)

  def check(ctxt: MLExpr[Context]): MLExpr[Option[Term]] =
    MLExpr.uncheckedLiteral[Context => Term => Term]("Syntax.check_term")(ctxt).liftTry(this)

  def certify(ctxt: MLExpr[Context]): MLExpr[Option[CTerm]] =
    MLExpr.uncheckedLiteral[Context => Term => CTerm]("Thm.cterm_of")(ctxt).liftTry(this)

  def evaluate(ctxt: MLExpr[Context]): MLExpr[Term] =
    MLExpr.uncheckedLiteral[Context => Term => Term]("Value.value")(ctxt)(this)

}

final case class Const(name: String, typ: Typ) extends Term
final case class Free(name: String, typ: Typ) extends Term
final case class Var(name: Indexname, typ: Typ) extends Term
final case class Bound(index: BigInt) extends Term
final case class Abs(name: String, typ: Typ, body: Term) extends Term
final case class App(fun: Term, arg: Term) extends Term
