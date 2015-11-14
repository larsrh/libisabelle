package edu.tum.cs.isabelle

import scala.math.BigInt
import scala.util.control.Exception._

import edu.tum.cs.isabelle.api._

import cats.std.either._
import cats.std.list._
import cats.syntax.traverse._

import acyclic.file

/** Combinators for [[Codec codecs]]. */
object Codec {

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

  private[isabelle] def text[A](to: A => String, from: String => Option[A]): Codec[A] = new Codec[A] {
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

  implicit def triple[A : Codec, B : Codec, C : Codec]: Codec[(A, B, C)] = Codec[(A, (B, C))].transform(
    { case (a, (b, c)) => (a, b, c)  },
    { case (a, b, c) => (a, (b, c)) }
  )

  /**
   * Template for a [[Codec codec]] for a sum type `A`.
   *
   * Each constructor of `A` should get assigned a unique index when
   * implementing this class. It is used to tag
   * [[edu.tum.cs.isabelle.api.Environment#XMLTree XML trees]], so that the
   * correct decoding function can be chosen.
   *
   * In addition to the contract of `[[Codec]]`, instances of this class must
   * also preserve the index, that is, any index returned by `[[enc]]` must
   * produce a valid decoding function in `[[dec]]`.
   *
   * Using this is a two-step process: First, create an object extending from
   * this abstract and implement `[[enc]]` and `[[dec]]` accordingly. Second,
   * call `[[toCodec]]` with an arbitrary tag on that object to obtain the
   * actual codec. This process exists to work around a bug in the Scala
   * compiler.
   *
   * ''Example usage''
   *
   * {{{
   * new Variant[Option[A]] {
   *   def enc(env: Environment, a: Option[A]): (Int, env.XMLTree) = a match {
   *     case Some(a) => (0, Codec[A].encode(env)(a))
   *     case None    => (1, Codec[Unit].encode(env)(()))
   *   }
   *   def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[Option[A]]] = idx match {
   *     case 0 => Some(Codec[A].decode(env)(_).right.map(Some.apply))
   *     case 1 => Some(Codec[Unit].decode(env)(_).right.map(_ => None))
   *     case _ => None
   *   }
   * } toCodec "option"
   * }}}
   *
   * Note the closing call to `[[toCodec]]`. The return type annotations are
   * required for Scala 2.10.x.
   *
   * ''Footnote''
   *
   * The preferred interface would look roughly like this:
   *
   * {{{
   * def variant[A](env: Environment)(enc: A => (Int, env.XMLTree), dec: Int => Option[env.XMLTree => Result[A]], tag: String): Codec[A]
   * }}}
   *
   * It can't be realised because some Scala versions have trouble establishing
   * equivalence between path-dependent types.
   */
  abstract class Variant[A] {

    /** Encode a value of the sum type into a tree and the index. */
    def enc(env: Environment, a: A): (Int, env.XMLTree)

    /**
     * Given an index, produce the corresponding decoding function or nothing
     * if the index is out of bounds.
     */
    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[A]]

    /** Turn an instance of this object into an actual `[[Codec]]`. */
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

  implicit def either[A : Codec, B : Codec]: Codec[Either[A, B]] = new Variant[Either[A, B]] {
    def enc(env: Environment, e: Either[A, B]): (Int, env.XMLTree) = e match {
      case Left(a)  => (0, Codec[A].encode(env)(a))
      case Right(b) => (1, Codec[B].encode(env)(b))
    }
    def dec(env: Environment, idx: Int): Option[env.XMLTree => XMLResult[Either[A, B]]] = idx match {
      case 0 => Some(Codec[A].decode(env)(_).right.map(Left.apply))
      case 1 => Some(Codec[B].decode(env)(_).right.map(Right.apply))
      case _ => None
    }
  } toCodec "either"

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
 * Since any [[edu.tum.cs.isabelle.api.Environment environment]] can define its
 * own [[edu.tum.cs.isabelle.api.Environment#XMLTree XML types]], instances of
 * this class need to be able to work with those abstractly. For this, any
 * environment provides constructors and destructors. See `[[encode]]` and
 * `[[decode]]` for their respective descriptions.
 *
 * For combinators to create codecs, refer to the [[Codec$ companion object]].
 *
 * ''Contract''
 *
 * Instances of this class must satisfy the following contract:
 * [[decode Decoding]] any value produced via `[[encode]]` must succeed,
 * yielding the original value. This only has to hold for one environment,
 * but not necessarily across environments.
 *
 * For the opposite direction, it is generally expected that a value which
 * cannot be produced via `[[encode]]` should not [[decode]] cleanly. This is
 * generally achieved by adding _tags_ to the trees. For example, if the codec
 * for type `Foo` for a given value would produce an XML document `t` before
 * tagging, the XML document after tagging would be `<tag name="foo">t</tag>`.
 * The `[[tagged]]` method transforms a raw codec into a tagged codec. Nested
 * tags are allowed (for example, when chaining multiple calls of
 * `[[tagged]]`), but produce additional overhead in the resulting XML
 * documents.
 */
trait Codec[T] { self =>

  /**
   * Encode a value into an
   * [[edu.tum.cs.isabelle.api.Environment#XMLTree XML tree]].
   *
   * You may want to use one of the two constructors
   * `[[edu.tum.cs.isabelle.api.Environment#elem elem]]` and
   * `[[edu.tum.cs.isabelle.api.Environment#text text]]`.
   *
   */
  def encode(env: Environment)(t: T): env.XMLTree

  /**
   * Decode a value from an
   * [[edu.tum.cs.isabelle.api.Environment#XMLTree XML tree]], or produce an
   * error.
   *
   * @see [[XMLResult]]
   */
  def decode(env: Environment)(tree: env.XMLTree): XMLResult[T]

  /**
   * Transform a codec for a type `T` into a codec for a type `U` by applying
   * one of the two specified conversions.
   *
   * After creating a new instance using `transform`, it is recommended to add
   * a new [[tagged tag]].
   */
  def transform[U](f: T => U, g: U => T): Codec[U] = new Codec[U] {
    def encode(env: Environment)(u: U): env.XMLTree = self.encode(env)(g(u))
    def decode(env: Environment)(tree: env.XMLTree) = self.decode(env)(tree).right.map(f)
  }

  /** Codec for a list of `T`s, tagged with the string `list`. */
  def list: Codec[List[T]] = new Codec[List[T]] {
    def encode(env: Environment)(ts: List[T]): env.XMLTree =
      Codec.addTag(env)("list", None, ts.map(t => self.encode(env)(t)))
    def decode(env: Environment)(tree: env.XMLTree) =
      Codec.expectTag(env)("list", tree).right.flatMap(_.traverse(self.decode(env)))
  }

  /** Codec for a pair of `T` and `U`, tagged with the string `tuple`. */
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
    def encode(env: Environment)(t: T): env.XMLTree =
      Codec.addTag(env)(tag, None, List(self.encode(env)(t)))
    def decode(env: Environment)(tree: env.XMLTree) =
      Codec.expectTag(env)(tag, tree).right.flatMap {
        case List(tree) => self.decode(env)(tree)
        case body => Left("invalid structure" -> body)
      }
  }

}
