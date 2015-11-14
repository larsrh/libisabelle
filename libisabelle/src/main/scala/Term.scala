package edu.tum.cs.isabelle

import scala.math.BigInt

import edu.tum.cs.isabelle.api._

import acyclic.file

object Typ {
  implicit lazy val typCodec: Codec[Typ] = new Codec.Variant[Typ] {
    lazy val typType = Codec[(String, List[Typ])]
    val typTFree = Codec[(String, Sort)]
    val typTVar = Codec[(Indexname, Sort)]

    def enc(env: Environment, typ: Typ): (Int, env.XMLTree) = typ match {
      case Type(name, args)  => (0, typType.encode(env)((name, args)))
      case TFree(name, sort) => (1, typTFree.encode(env)((name, sort)))
      case TVar(iname, sort) => (2, typTVar.encode(env)((iname, sort)))
    }

    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[Typ]] = idx match {
      case 0 => Some(tree => typType.decode(env)(tree).right.map  { case (name, args) => Type(name, args) })
      case 1 => Some(tree => typTFree.decode(env)(tree).right.map { case (name, sort) => TFree(name, sort) })
      case 2 => Some(tree => typTVar.decode(env)(tree).right.map  { case (iname, sort) => TVar(iname, sort) })
      case _ => None
    }
  }.toCodec("Pure.typ")

  val dummyT = Type("dummy", Nil)
}

sealed abstract class Typ
case class Type(name: String, args: List[Typ] = Nil) extends Typ
case class TFree(name: String, sort: Sort) extends Typ
case class TVar(name: Indexname, sort: Sort) extends Typ

object Term {
  implicit lazy val termCodec: Codec[Term] = new Codec.Variant[Term] {
    val termConst = Codec[(String, Typ)]
    val termFree = Codec[(String, Typ)]
    val termVar = Codec[(Indexname, Typ)]
    val termBound = Codec[BigInt]
    lazy val termAbs = Codec[(String, Typ, Term)]
    lazy val termApp = Codec[(Term, Term)]

    def enc(env: Environment, term: Term): (Int, env.XMLTree) = term match {
      case Const(name, typ)     => (0, termConst.encode(env)((name, typ)))
      case Free(name, typ)      => (1, termFree.encode(env)((name, typ)))
      case Var(iname, typ)      => (2, termVar.encode(env)((iname, typ)))
      case Bound(idx)           => (3, termBound.encode(env)(idx))
      case Abs(name, typ, body) => (4, termAbs.encode(env)((name, typ, body)))
      case App(f, x)            => (5, termApp.encode(env)((f, x)))
    }

    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[Term]] = idx match {
      case 0 => Some(tree => termConst.decode(env)(tree).right.map  { case (name, typ) => Const(name, typ) })
      case 1 => Some(tree => termFree.decode(env)(tree).right.map   { case (name, typ) => Free(name, typ) })
      case 2 => Some(tree => termVar.decode(env)(tree).right.map    { case (iname, typ) => Var(iname, typ) })
      case 3 => Some(tree => termBound.decode(env)(tree).right.map  { idx => Bound(idx) })
      case 4 => Some(tree => termAbs.decode(env)(tree).right.map    { case (name, typ, body) => Abs(name, typ, body) })
      case 5 => Some(tree => termApp.decode(env)(tree).right.map    { case (f, x) => App(f, x) })
      case _ => None
    }
  }.toCodec("Pure.term")
}

sealed abstract class Term
case class Const(name: String, typ: Typ) extends Term
case class Free(name: String, typ: Typ) extends Term
case class Var(name: Indexname, typ: Typ) extends Term
case class Bound(index: BigInt) extends Term
case class Abs(name: String, typ: Typ, body: Term) extends Term
case class App(fun: Term, arg: Term) extends Term
