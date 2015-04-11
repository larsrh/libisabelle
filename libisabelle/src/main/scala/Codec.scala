package edu.tum.cs.isabelle

import scala.math.BigInt

import isabelle._


object Codec {

  private def addTag(tag: String, idx: Option[Int], body: XML.Body): XML.Tree =
    XML.Elem(Markup("tag", ("type" -> tag) :: idx.map(i => List("idx" -> i.toString)).getOrElse(Nil)), body)

  private def expectTag(tag: String, tree: XML.Tree) =
    tree match {
      case XML.Elem(Markup("tag", List(("type", tag0))), body) =>
        if (tag == tag0)
          Right(body)
        else
          Left("tag mismatch" -> List(tree))
      case _ =>
        Left("tag expected" -> List(tree))
    }

  private def expectIndexedTag(tag: String, tree: XML.Tree) =
    tree match {
      case XML.Elem(Markup("tag", List(("type", tag0), ("idx", i))), body) =>
        if (tag == tag0)
          try {
            Right(i.toInt -> body)
          } catch {
            case ex: NumberFormatException =>
              Left(ex.toString -> List(tree))
          }
        else
          Left("tag mismatch" -> List(tree))
      case _ =>
        Left("indexed tag expected" -> List(tree))
    }

  def id: Codec[XML.Tree] = new Codec[XML.Tree] {
    def encode(t: XML.Tree) = t
    def decode(tree: XML.Tree) = Right(tree)
  }


  implicit def string: Codec[String] = new Codec[String] {
    // FIXME replace by own routines with escape handling
    def encode(t: String) = addTag("string", None, XML.Encode.string(t))
    def decode(tree: XML.Tree) =
      expectTag("string", tree).right.flatMap { body =>
        try {
          Right(XML.Decode.string(body))
        } catch {
          case err: XML.Error =>
            Left("decoding failed" -> body)
        }
      }
  }

  implicit def integer: Codec[BigInt] = new Codec[BigInt] {
    def encode(t: BigInt) = addTag("int", None, XML.Encode.string(t.toString))
    def decode(tree: XML.Tree) =
      expectTag("int", tree).right.flatMap { body =>
        try {
          Right(BigInt(XML.Decode.string(body)))
        } catch {
          case _: XML.Error | _: NumberFormatException =>
            Left("decoding failed" -> body)
        }
      }
  }

  implicit def unit: Codec[Unit] = new Codec[Unit] {
    def encode(u: Unit) = addTag("unit", None, Nil)
    def decode(tree: XML.Tree) =
      expectTag("unit", tree).right.flatMap {
        case Nil => Right(())
        case body => Left("expected nothing" -> body)
      }
  }

  implicit def list[A : Codec]: Codec[List[A]] =
    Codec[A].list

  implicit def tuple[A : Codec, B : Codec]: Codec[(A, B)] =
    Codec[A] tuple Codec[B]

  def variant[A](enc: A => (Int, XML.Tree), tryDec: Int => Option[XML.Tree => Result[A]], tag: String): Codec[A] = new Codec[A] {
    def encode(a: A) = {
      val (idx, tree) = enc(a)
      addTag(tag, Some(idx), List(tree))
    }
    def decode(tree: XML.Tree) = expectIndexedTag(tag, tree).right.flatMap {
      case (idx, List(tree)) =>
        tryDec(idx) match {
          case Some(dec) => dec(tree)
          case None => Left(s"invalid index $idx" -> List(tree))
        }
      case _ =>
        Left("invalid structure" -> List(tree))
    }
  }

  implicit def option[A : Codec]: Codec[Option[A]] =
    variant(
      _.fold((1, Codec[Unit].encode(())))(a => (0, Codec[A].encode(a))),
      {
        case 0 => Some(Codec[A].decode(_).right.map(Some.apply))
        case 1 => Some(Codec[Unit].decode(_).right.map(_ => None))
        case _ => None
      },
      "option"
    )

  implicit def tree: Codec[XML.Tree] = id.tagged("XML.tree")
  implicit def body: Codec[XML.Body] = id.list

  implicit def exn: Codec[Throwable] =
    Codec[String].transform(new RuntimeException(_), _.getMessage)

  implicit def exnResult[A : Codec]: Codec[Exn.Result[A]] =
    variant(
      {
        case Exn.Res(a) => (0, Codec[A].encode(a))
        case Exn.Exn(e) => (1, Codec[Throwable].encode(e))
      },
      {
        case 0 => Some(Codec[A].decode(_).right.map(Exn.Res.apply))
        case 1 => Some(Codec[Throwable].decode(_).right.map(Exn.Exn.apply))
        case _ => None
      },
      "Exn.result"
    )

  def apply[A](implicit A: Codec[A]): Codec[A] = A

}

trait Codec[T] { self =>

  def encode(t: T): XML.Tree
  def decode(tree: XML.Tree): Result[T]

  def transform[U](f: T => U, g: U => T): Codec[U] = new Codec[U] {
    def encode(u: U) = self.encode(g(u))
    def decode(tree: XML.Tree) = self.decode(tree).right.map(f)
  }

  def list: Codec[List[T]] = new Codec[List[T]] {
    def encode(ts: List[T]) =
      Codec.addTag("list", None, ts.map(self.encode))
    def decode(tree: XML.Tree) =
      Codec.expectTag("list", tree).right.flatMap(_.traverse(self.decode))
  }

  def tuple[U](that: Codec[U]): Codec[(T, U)] = new Codec[(T, U)] {
    def encode(tu: (T, U)) =
      Codec.addTag("tuple", None, List(self.encode(tu._1), that.encode(tu._2)))
    def decode(tree: XML.Tree) =
      Codec.expectTag("tuple", tree).right.flatMap {
        case List(x, y) =>
          for { t <- self.decode(x).right; u <- that.decode(y).right } yield (t, u)
        case body =>
          Left("invalid structure" -> body)
      }
  }

  def tagged(tag: String): Codec[T] = new Codec[T] {
    def encode(t: T) = Codec.addTag(tag, None, List(self.encode(t)))
    def decode(tree: XML.Tree) =
      Codec.expectTag(tag, tree).right.flatMap {
        case List(tree) => self.decode(tree)
        case body => Left("invalid structure" -> body)
      }
  }

}
