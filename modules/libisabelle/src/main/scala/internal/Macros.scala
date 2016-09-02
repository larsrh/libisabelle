package info.hupel.isabelle.internal

import scala.language.experimental.macros
import scala.reflect.macros.whitebox

import cats.instances.list._
import cats.syntax.traverse._

import info.hupel.isabelle._
import info.hupel.isabelle.pure._

import macrocompat.bundle

object Macros {

  def fuse(terms: List[Program[Term]], parts: List[String]): Program[String] = {
    val tps: Program[List[String]] =
      terms.sequence.map { ts =>
        ts zip parts.tail map { case (t, p) =>
          val s = Codec[Term].encode(t).compact
          s"XML\\<open>$s\\<close>$p"
        }
      }

    tps.map(_.mkString("")).map(parts.head + _)
  }

}

@bundle
class Macros(val c: whitebox.Context { type PrefixType <: ExprStringContext.term.type }) {

  import c.universe.{Expr => _, _}

  // FIXME this should be c.prefix, debug MatchError
  private val q"""${_}(${_}(..$parts)).${_}.apply[..${_}](..${_})""" =
    c.macroApplication

  private def embed(arg: Tree): Tree = {
    if (arg.tpe <:< typeOf[Term])
      q"""_root_.info.hupel.isabelle.MLProg.pure($arg)"""
    else if (arg.tpe <:< typeOf[Expr[_]])
      q"""_root_.info.hupel.isabelle.MLProg.pure($arg.term)"""
    else {
      val embeddable = c.inferImplicitValue(appliedType(typeOf[Embeddable[_]], arg.tpe))
      if (embeddable == EmptyTree)
        c.error(c.enclosingPosition, s"Could not find implicit `Embeddable` for type ${arg.tpe}")
      q"""$embeddable.embed($arg)"""
    }
  }

  def term(args: Tree*): c.Expr[Program[String]] = {
    val terms = args.map(embed)
    val termList = c.Expr[List[Program[Term]]](q"""_root_.scala.List(..$terms)""")
    val partList = c.Expr[List[String]](q"""_root_.scala.List(..$parts)""")
    reify {
      Macros.fuse(termList.splice, partList.splice)
    }
  }

}
