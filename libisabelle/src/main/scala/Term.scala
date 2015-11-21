package edu.tum.cs.isabelle

import scala.math.BigInt

import edu.tum.cs.isabelle.api._

import acyclic.file

object Typ {
  implicit lazy val typCodec: Codec[Typ] = new Codec.Variant[Typ]("Pure.typ") {
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

  val dummyT = Type("dummy", Nil)
}

sealed abstract class Typ
case class Type(name: String, args: List[Typ] = Nil) extends Typ
case class TFree(name: String, sort: Sort) extends Typ
case class TVar(name: Indexname, sort: Sort) extends Typ

object Term {
  implicit lazy val termCodec: Codec[Term] = new Codec.Variant[Term]("Pure.term") {
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
}

sealed abstract class Term
case class Const(name: String, typ: Typ) extends Term
case class Free(name: String, typ: Typ) extends Term
case class Var(name: Indexname, typ: Typ) extends Term
case class Bound(index: BigInt) extends Term
case class Abs(name: String, typ: Typ, body: Term) extends Term
case class App(fun: Term, arg: Term) extends Term
