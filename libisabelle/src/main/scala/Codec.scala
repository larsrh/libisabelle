package edu.tum.cs.isabelle

import scala.math.BigInt
import scala.util.control.Exception._
import scala.util.control.NoStackTrace

import edu.tum.cs.isabelle.api._

object Codec {

  case class ProverException(msg: String) extends RuntimeException(msg) with NoStackTrace

  private def addTag(env: Environment)(tag: String, idx: Option[Int], body: env.XMLBody) =
    env.elem(("tag", ("type" -> tag) :: idx.map(i => List("idx" -> i.toString)).getOrElse(Nil)), body)

  private def expectTag(env: Environment)(tag: String, tree: env.XMLTree) =
    env.destTree(tree) match {
      case Right((("tag", List(("type", tag0))), body)) =>
        if (tag == tag0)
          Right(body)
        else
          Left("tag mismatch" -> List(tree))
      case _ =>
        Left("tag expected" -> List(tree))
    }

  private def expectIndexedTag(env: Environment)(tag: String, tree: env.XMLTree) =
    env.destTree(tree) match {
      case Right((("tag", List(("type", tag0), ("idx", i))), body)) =>
        if (tag == tag0)
          try {
            Right(i.toInt -> body)
          }
          catch {
            case ex: NumberFormatException =>
              Left(ex.toString -> List(tree))
          }
        else
          Left("tag mismatch" -> List(tree))
      case _ =>
        Left("indexed tag expected" -> List(tree))
    }

  private def text[A](to: A => String, from: String => Option[A]): Codec[A] = new Codec[A] {
    // FIXME escape handling
    def encode(env: Environment)(t: A): env.XMLTree = env.text(to(t))
    def decode(env: Environment)(tree: env.XMLTree) = env.destTree(tree) match {
      case Left(content) =>
        from(content) match {
          case Some(a) => Right(a)
          case None => Left("decoding failed" -> List(tree))
        }
      case _ =>
        Left("expected text tree" -> List(tree))
    }
  }

  implicit def string: Codec[String] = text[String](identity, Some.apply).tagged("string")

  implicit def integer: Codec[BigInt] = text[BigInt](
    _.toString,
    str => catching(classOf[NumberFormatException]) opt BigInt(str)
  ).tagged("int")

  implicit def boolean: Codec[Boolean] = text[Boolean](
    _.toString,
    {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    }
  ).tagged("bool")

  implicit def unit: Codec[Unit] = new Codec[Unit] {
    def encode(env: Environment)(u: Unit): env.XMLTree = addTag(env)("unit", None, Nil)
    def decode(env: Environment)(tree: env.XMLTree) =
      expectTag(env)("unit", tree).right.flatMap {
        case Nil => Right(())
        case body => Left("expected nothing" -> body)
      }
  }

  implicit def list[A : Codec]: Codec[List[A]] =
    Codec[A].list

  implicit def tuple[A : Codec, B : Codec]: Codec[(A, B)] =
    Codec[A] tuple Codec[B]

  // FIXME can't inline because scalac bug
  // used to be this:
  // def variant[A]
  //   (env: Environment)
  //   (enc: A => (Int, env.XMLTree), tryDec: Int => Option[env.XMLTree => Result[A]], tag: String): Codec[A]
  abstract class Variant[A] {
    def enc(env: Environment, a: A): (Int, env.XMLTree)
    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[A]]

    def toCodec(tag: String): Codec[A] = new Codec[A] {
      def encode(env: Environment)(a: A): env.XMLTree = {
        val (idx, tree) = enc(env, a)
        addTag(env)(tag, Some(idx), List(tree))
      }
      def decode(env: Environment)(tree: env.XMLTree) = expectIndexedTag(env)(tag, tree).right.flatMap {
        case (idx, List(tree)) =>
          dec(env, idx) match {
            case Some(dec) => dec(tree)
            case None => Left(s"invalid index $idx" -> List(tree))
          }
        case _ =>
          Left("invalid structure" -> List(tree))
      }
    }

  }

  implicit def option[A : Codec]: Codec[Option[A]] = new Variant[Option[A]] {
    def enc(env: Environment, a: Option[A]): (Int, env.XMLTree) = a match {
      case Some(a) => (0, Codec[A].encode(env)(a))
      case None    => (1, Codec[Unit].encode(env)(()))
    }
    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[Option[A]]] = idx match {
      case 0 => Some(Codec[A].decode(env)(_).right.map(Some.apply))
      case 1 => Some(Codec[Unit].decode(env)(_).right.map(_ => None))
      case _ => None
    }
  } toCodec "option"

  implicit def exn: Codec[Throwable] = text[Throwable](
    _.getMessage,
    str => Some(ProverException(str))
  ).tagged("exn")

  implicit def proverResult[A : Codec]: Codec[ProverResult[A]] = new Variant[ProverResult[A]] {
    def enc(env: Environment, a: ProverResult[A]): (Int, env.XMLTree) = a match {
      case Right(a) => (0, Codec[A].encode(env)(a))
      case Left(e)  => (1, Codec[Throwable].encode(env)(e))
    }
    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[ProverResult[A]]] = idx match {
      case 0 => Some(Codec[A].decode(env)(_).right.map(Right.apply))
      case 1 => Some(Codec[Throwable].decode(env)(_).right.map(Left.apply))
      case _ => None
    }
  } toCodec "Exn.result"

  def apply[A](implicit A: Codec[A]): Codec[A] = A

}

trait Codec[T] { self =>

  def encode(env: Environment)(t: T): env.XMLTree
  def decode(env: Environment)(tree: env.XMLTree): XMLResult[T]

  def transform[U](f: T => U, g: U => T): Codec[U] = new Codec[U] {
    def encode(env: Environment)(u: U): env.XMLTree = self.encode(env)(g(u))
    def decode(env: Environment)(tree: env.XMLTree) = self.decode(env)(tree).right.map(f)
  }

  def list: Codec[List[T]] = new Codec[List[T]] {
    def encode(env: Environment)(ts: List[T]): env.XMLTree =
      Codec.addTag(env)("list", None, ts.map(t => self.encode(env)(t)))
    def decode(env: Environment)(tree: env.XMLTree) =
      Codec.expectTag(env)("list", tree).right.flatMap(_.traverse(self.decode(env)))
  }

  def tuple[U](that: Codec[U]): Codec[(T, U)] = new Codec[(T, U)] {
    def encode(env: Environment)(tu: (T, U)): env.XMLTree =
      Codec.addTag(env)("tuple", None, List(self.encode(env)(tu._1), that.encode(env)(tu._2)))
    def decode(env: Environment)(tree: env.XMLTree) =
      Codec.expectTag(env)("tuple", tree).right.flatMap {
        case List(x, y) =>
          for { t <- self.decode(env)(x).right; u <- that.decode(env)(y).right } yield (t, u)
        case body =>
          Left("invalid structure" -> body)
      }
  }

  def tagged(tag: String): Codec[T] = new Codec[T] {
    def encode(env: Environment)(t: T): env.XMLTree = Codec.addTag(env)(tag, None, List(self.encode(env)(t)))
    def decode(env: Environment)(tree: env.XMLTree) =
      Codec.expectTag(env)(tag, tree).right.flatMap {
        case List(tree) => self.decode(env)(tree)
        case body => Left("invalid structure" -> body)
      }
  }

}

abstract class SimpleCodec[T] extends Codec[T] {

  def enc(t: T): XMLTree
  def dec(tree: XMLTree): XMLResult[T]

  override final def encode(env: Environment)(t: T): env.XMLTree =
    enc(t).toGeneric(env)

  override final def decode(env: Environment)(tree: env.XMLTree): XMLResult[T] =
    dec(XMLTree.fromGeneric(env)(tree))

}
