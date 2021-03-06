package info.hupel.isabelle

import scala.math.BigInt
import scala.util.control.Exception._

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._

import info.hupel.isabelle.api._

/** Combinators for [[Codec codecs]]. */
object Codec {

  private def addTag(tag: String, idx: Option[Int], body: XML.Body) =
    XML.elem(("tag", ("type" -> tag) :: idx.map(i => List("idx" -> i.toString)).getOrElse(Nil)), body)

  private def expectTag(tag: String, tree: XML.Tree) = tree match {
    case XML.Elem(("tag", List(("type", tag0))), body) =>
        if (tag == tag0)
          Right(body)
        else
          Left("tag mismatch" -> List(tree))
    case _ =>
      Left("tag expected" -> List(tree))
  }

  private def expectIndexedTag(tag: String, tree: XML.Tree) = tree match {
    case XML.Elem(("tag", List(("type", tag0), ("idx", i))), body) =>
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

  private[isabelle] def text[A](to: A => String, from: String => Option[A], mlType0: String): Codec[A] = new Codec[A] {
    val mlType = mlType0

    def escape(c: Char) = c match {
      case '<' => "&lt;"
      case '>' => "&gt;"
      case '&' => "&amp;"
      case '\'' => "&apos;"
      case '"' => "&quot;"
      case _ =>
        val n = c.toInt
        if (n < 32) s"&#$n;"
        else if (n < 127) c.toString
        else s"&#$n;"
    }

    val EntityPrefix = "&([^;]*);(.*)".r

    def unescapeSingle(s: String) = s match {
      case "lt" => '<'
      case "gt" => '>'
      case "amp" => '&'
      case "apos" => '\''
      case "quot" => '"'
      case _ if s.startsWith("#") => s.tail.toInt.toChar
    }

    def unescape(s: String): String = s match {
      case "" => ""
      case EntityPrefix(entity, rest) => unescapeSingle(entity).toString + unescape(rest)
      case _ => s.head.toString + unescape(s.tail)
    }

    def encode(t: A) = XML.elem(("text", List("content" -> to(t).flatMap(escape))), Nil)
    def decode(tree: XML.Tree) = tree match {
      case XML.Elem(("text", List(("content", content))), Nil) =>
        from(unescape(content)) match {
          case Some(a) => Right(a)
          case None => Left("decoding failed" -> List(tree))
        }
      case _ =>
        Left("expected text tree" -> List(tree))
    }
  }

  implicit def string: Codec[String] = text[String](identity, Some.apply, "string").tagged("string")

  implicit def integer: Codec[BigInt] = text[BigInt](
    _.toString,
    str => catching(classOf[NumberFormatException]) opt BigInt(str),
    "int"
  ).tagged("int")

  implicit def boolean: Codec[Boolean] = text[Boolean](
    _.toString,
    {
      case "true" => Some(true)
      case "false" => Some(false)
      case _ => None
    },
    "bool"
  ).tagged("bool")

  implicit def unit: Codec[Unit] = new Codec[Unit] {
    val mlType = "unit"
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

  implicit def triple[A : Codec, B : Codec, C : Codec]: Codec[(A, B, C)] = Codec[(A, (B, C))].transform[(A, B, C)](
    { case (a, (b, c)) => (a, b, c)  },
    { case (a, b, c) => (a, (b, c)) },
    s"(${Codec[A].mlType}) * (${Codec[B].mlType}) * (${Codec[C].mlType})"
  )

  /**
   * Template for a [[Codec codec]] for a sum type `A`.
   *
   * Each constructor of `A` should get assigned a unique index when
   * implementing this class. It is used to tag
   * [[info.hupel.isabelle.api.XML.Tree XML trees]], so that the correct
   * decoding function can be chosen.
   *
   * In addition to the contract of `[[Codec]]`, instances of this class must
   * also preserve the index, that is, any index returned by `[[enc]]` must
   * produce a valid decoding function in `[[dec]]`.
   *
   * Using this is simple: Instead of creating a new `[[Codec]]` object, create
   * an object extending from this class and implement `[[enc]]` and `[[dec]]`
   * accordingly. It is automatically a `[[Codec]]`.
   *
   * ''Example usage''
   *
   * {{{
   * new Variant[Option[A]]("option") {
   *   def enc(a: Option[A]) = a match {
   *     case Some(a) => (0, Codec[A].encode(a))
   *     case None    => (1, Codec[Unit].encode(()))
   *   }
   *   def dec(idx: Int) = idx match {
   *     case 0 => Some(Codec[A].decode(_).right.map(Some.apply))
   *     case 1 => Some(Codec[Unit].decode(_).right.map(_ => None))
   *     case _ => None
   *   }
   * }
   * }}}
   */
  abstract class Variant[A](tag: String) extends Codec[A] {

    /** Encode a value of the sum type into a tree and the index. */
    protected def enc(a: A): (Int, XML.Tree)

    /**
     * Given an index, produce the corresponding decoding function or nothing
     * if the index is out of bounds.
     */
    protected def dec(idx: Int): Option[XML.Tree => XMLResult[A]]

    def encode(a: A) = {
      val (idx, tree) = enc(a)
      addTag(tag, Some(idx), List(tree))
    }

    def decode(tree: XML.Tree) = expectIndexedTag(tag, tree).right.flatMap {
      case (idx, List(tree)) =>
        dec(idx) match {
          case Some(dec) => dec(tree)
          case None => Left(s"invalid index $idx" -> List(tree))
        }
      case _ =>
        Left(s"invalid structure in variant $tag" -> List(tree))
    }

  }

  implicit def option[A : Codec]: Codec[Option[A]] = new Variant[Option[A]]("option") {
    val mlType = s"(${Codec[A].mlType}) option"
    def enc(a: Option[A]) = a match {
      case Some(a) => (0, Codec[A].encode(a))
      case None    => (1, Codec[Unit].encode(()))
    }
    def dec(idx: Int) = idx match {
      case 0 => Some(Codec[A].decode(_).right.map(Some.apply))
      case 1 => Some(Codec[Unit].decode(_).right.map(_ => None))
      case _ => None
    }
  }

  implicit def either[A : Codec, B : Codec]: Codec[Either[A, B]] = new Variant[Either[A, B]]("either") {
    val mlType = s"(${Codec[A].mlType}, ${Codec[B].mlType}) Codec.either"
    def enc(e: Either[A, B]) = e match {
      case Left(a)  => (0, Codec[A].encode(a))
      case Right(b) => (1, Codec[B].encode(b))
    }
    def dec(idx: Int) = idx match {
      case 0 => Some(Codec[A].decode(_).right.map(Left.apply))
      case 1 => Some(Codec[B].decode(_).right.map(Right.apply))
      case _ => None
    }
  }

  implicit def tree: Codec[XML.Tree] = new Codec[XML.Tree] {
    val mlType = "XML.tree"
    def encode(t: XML.Tree) = t
    def decode(tree: XML.Tree) = Right(tree)
  }.tagged("XML.tree")

  /**
   * Obtain an instance of a codec from the implicit scope.
   *
   * ''Example usage''
   * {{{
   * Codec[Int].encode(env)(3)
   * }}}
   */
  def apply[A](implicit A: Codec[A]): Codec[A] = A

}

/**
 * A type class representing the ability to convert a type to and from an XML
 * representation.
 *
 * For combinators to create codecs, refer to the [[Codec$ companion object]].
 *
 * ''Contract''
 *
 * Instances of this class must satisfy the following contract:
 * [[decode Decoding]] any value produced via `[[encode]]` must succeed,
 * yielding the original value. The `[[mlType]]` field must contain a string
 * representation of the corresponding type in Isabelle/ML.
 *
 * For the opposite direction, it is generally expected that a value which
 * cannot be produced via `[[encode]]` should not [[decode]] cleanly. This is
 * generally achieved by adding ''tags'' to the trees. For example, if the
 * codec for type `Foo` for a given value would produce an XML document `t`
 * before tagging, the XML document after tagging would be
 * `<tag name="foo">t</tag>`. The `[[tagged]]` method transforms a raw codec
 * into a tagged codec. Nested tags are allowed (for example, when chaining
 * multiple calls of `[[tagged]]`), but produce additional overhead in the
 * resulting XML documents.
 */
trait Codec[T] { self =>

  /** String representation of the type corresponding to `T` in Isabelle/ML. */
  val mlType: String

  /**
   * Encode a value into an
   * [[info.hupel.isabelle.api.XML.Tree XML tree]].
   *
   * You may want to use one of the two constructors
   * `[[info.hupel.isabelle.api.XML.elem elem]]` and
   * `[[info.hupel.isabelle.api.XML.text text]]`.
   *
   */
  def encode(t: T): XML.Tree

  /**
   * Decode a value from an
   * [[info.hupel.isabelle.api.XML.Tree XML tree]], or produce an
   * error.
   *
   * @see [[XMLResult]]
   */
  def decode(tree: XML.Tree): XMLResult[T]

  final def decodeOrThrow(tree: XML.Tree): T = decode(tree) match {
    case Left((err, body)) => throw DecodingException(err, body)
    case Right(o) => o
  }

  /**
   * Transform a codec for a type `T` into a codec for a type `U` by applying
   * one of the two specified conversions.
   *
   * After creating a new instance using `transform`, it is recommended to add
   * a new [[tagged tag]].
   */
  def transform[U](f: T => U, g: U => T, mlType0: String): Codec[U] = new Codec[U] {
    val mlType = mlType0
    def encode(u: U): XML.Tree = self.encode(g(u))
    def decode(tree: XML.Tree) = self.decode(tree).right.map(f)
  }

  def ptransform[U](f: T => Option[U], g: U => T, mlType0: String): Codec[U] = new Codec[U] {
    val mlType = mlType0
    def encode(u: U): XML.Tree = self.encode(g(u))
    def decode(tree: XML.Tree) = self.decode(tree) match {
      case Left(err) => Left(err)
      case Right(t) => f(t).map(Right.apply).getOrElse(Left("transformation failed" -> List(tree)))
    }
  }

  /** Codec for a list of `T`s, tagged with the string `list`. */
  def list: Codec[List[T]] = new Codec[List[T]] {
    val mlType = s"(${self.mlType}) list"
    def encode(ts: List[T]): XML.Tree =
      Codec.addTag("list", None, ts.map(t => self.encode(t)))
    def decode(tree: XML.Tree) =
      Codec.expectTag("list", tree).right.flatMap(_.traverse(self.decode))
  }

  /** Codec for a pair of `T` and `U`, tagged with the string `tuple`. */
  def tuple[U](that: Codec[U]): Codec[(T, U)] = new Codec[(T, U)] {
    val mlType = s"(${self.mlType}) * (${that.mlType})"
    def encode(tu: (T, U)): XML.Tree =
      Codec.addTag("tuple", None, List(self.encode(tu._1), that.encode(tu._2)))
    def decode(tree: XML.Tree) =
      Codec.expectTag("tuple", tree).right.flatMap {
        case List(x, y) =>
          for { t <- self.decode(x).right; u <- that.decode(y).right } yield (t, u)
        case body =>
          Left("invalid structure in tuple" -> body)
      }
  }

  /**
   * Create an identical codec which adds (or expects, respectively) an
   * additional top-level tag.
   *
   * The given tag can be anything acceptable as an XML attribute value, but
   * should be globally unique. That is, no other codec should have the same
   * tag. It is fine if there are multiple codecs for a type, as long as their
   * tags are distinct, although that is not a requirement.
   */
  def tagged(tag: String): Codec[T] = new Codec[T] {
    val mlType = self.mlType
    def encode(t: T): XML.Tree =
      Codec.addTag(tag, None, List(self.encode(t)))
    def decode(tree: XML.Tree) =
      Codec.expectTag(tag, tree).right.flatMap {
        case List(tree) => self.decode(tree)
        case body => Left(s"invalid structure in tagged $tag" -> body)
      }
  }

}
