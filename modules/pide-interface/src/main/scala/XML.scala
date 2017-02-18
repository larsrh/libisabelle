package info.hupel.isabelle.api

import scala.annotation.tailrec
import scala.collection.mutable.ListBuffer

import cats.instances.list._
import cats.syntax.traverse._

import scodec._
import scodec.bits.BitVector
import scodec.codecs._
import scodec.interop.cats._

// FIXME code mostly copied from xml.scala and yxml.scala

object XML {

  private val X = '\u0005'
  private val Y = '\u0006'

  private def prettyEscape(string: String) = string
    .replace("&", "&amp;")
    .replace("<", "&lt;")
    .replace(">", "&gt;")
    .replace("\"", "&quot;")
    .replace("'", "&apos;")

  sealed abstract class Tree {
    def toYXML: String = bodyToYXML(List(this))
    def pretty(indent: Int): String
    final def pretty: String = pretty(0)
    def compact: String
    def stripMarkup: String
  }

  final case class Elem(markup: Markup, body: Body) extends Tree {
    def pretty(indent: Int) = {
      val attrs = (if (markup._2.isEmpty) "" else " ") + markup._2.map { case (k, v) => s"$k='${prettyEscape(v)}'" }.mkString(" ")
      if (body.isEmpty) {
        " " * indent + "<" + markup._1 + attrs + " />"
      }
      else {
        val head = " " * indent + "<" + markup._1 + attrs + ">"
        val rows = body.map(_.pretty(indent + 2)).mkString("\n", "\n", "\n")
        val foot = " " * indent + "</" + markup._1 + ">"
        head + rows + foot
      }
    }
    def compact = {
      val (name, attrs) = markup
      val compactAttrs = attrs.map { case (k, v) => s"""$k="$v"""" }.mkString(" ")
      s"<$name $compactAttrs>${body.map(_.compact).mkString("")}</$name>"
    }
    def stripMarkup = body.map(_.stripMarkup).mkString(" ")
  }

  final case class Text(content: String) extends Tree {
    def pretty(indent: Int) =
      " " * indent + prettyEscape(content)
    def compact = content
    def stripMarkup = content
  }

  type Body = List[Tree]

  @inline
  def elem(markup: Markup, body: Body): Tree = Elem(markup, body)

  @inline
  def text(content: String): Tree = Text(content)

  private def isValidIdentifier(c: Char): Boolean =
    c != X && c != Y && c != '=' && c < 128
  private def isValidText(c: Char): Boolean =
    c != X && c != Y

  def charClass(valid: Char => Boolean): Codec[Char] = new Codec[Char] {
    def sizeBound = SizeBound(lowerBound = 8, upperBound = Some(8))
    def encode(c: Char): Attempt[BitVector] =
      if (valid(c))
        Attempt.successful(BitVector(c.toByte))
      else
        Attempt.failure(Err(s"invalid character: '$c'"))
    def decode(buf: BitVector): Attempt[DecodeResult[Char]] = buf.consumeThen(8)(
      _ => Attempt.failure(Err.insufficientBits(8, buf.size)),
      { (h, t) =>
        val c = h.toByte().toChar
        if (valid(c))
          Attempt.successful(DecodeResult(c, t))
        else
          Attempt.failure(Err(s"invalid character: '$c'"))
      }
    )
  }

  implicit final class BitVectorOps(val vector: BitVector) extends AnyVal {
    def chunked(len: Long): (List[BitVector], BitVector) = {
      require(len > 0)
      @tailrec def go(v: BitVector, vs: List[BitVector]): (List[BitVector], BitVector) =
        if (v.sizeLessThan(len))
          (vs.reverse, v)
        else {
          val (h, t) = vector.splitAt(len)
          go(t, h :: vs)
        }
      go(vector, Nil)
    }
  }

  def until[A](delimiter: BitVector, codec: Codec[A]): Codec[A] =
    if (delimiter.size == 0) codec
    else new Codec[A] {
      def sizeBound = codec.sizeBound + SizeBound.exact(delimiter.size)
      def encode(a: A): Attempt[BitVector] =
        codec.encode(a).flatMap { buf =>
          if (buf.length % delimiter.size == 0)
            Attempt.successful(buf ++ delimiter)
          else
            Attempt.failure(Err(s"${buf.length} is not a multiple of ${delimiter.size}"))
        }
      def decode(buf: BitVector): Attempt[DecodeResult[A]] = {
        val (chunks, rest) = buf.chunked(delimiter.size)
        val (h, t) = chunks.span(_ != delimiter)
        t match {
          case Nil => Attempt.failure(Err(s"delimiter '$delimiter' not found"))
          case _ :: t =>
            val init = BitVector.concat(h)
            codec.decode(init).flatMap { case DecodeResult(a, remainder) =>
              if (remainder.isEmpty)
                Attempt.successful(DecodeResult(a, rest))
              else
                Attempt.failure(Err("left-over buffer"))
            }
        }
      }
    }

  def optional[A](codec: Codec[A]): Codec[Option[A]] = new Codec[Option[A]] {
    def sizeBound = SizeBound(lowerBound = 0, upperBound = codec.sizeBound.upperBound)
    def encode(a: Option[A]): Attempt[BitVector] = a match {
      case None => Attempt.successful(BitVector.empty)
      case Some(a) => codec.encode(a)
    }
    def decode(buf: BitVector): Attempt[DecodeResult[Option[A]]] =
      codec.decode(buf).map(_.map(Some(_))).recover { case _ =>
        DecodeResult(None, buf)
      }
  }

  def repeat[A](codec: Codec[A]): Codec[List[A]] = new Codec[List[A]] {
    def sizeBound = SizeBound.atLeast(0)
    def encode(a: List[A]): Attempt[BitVector] =
      a.traverseU(codec.encode).map(BitVector.concat)
    def decode(buf: BitVector): Attempt[DecodeResult[List[A]]] = {
      @tailrec def go(buf: BitVector, xs: List[A]): (List[A], BitVector) =
        if (buf.sizeLessThan(1))
          (xs.reverse, buf)
        else
          codec.decode(buf) match {
            case Attempt.Successful(DecodeResult(a, rest)) => go(rest, a :: xs)
            case Attempt.Failure(_) => (xs.reverse, buf)
          }
      val (xs, rest) = go(buf, Nil)
      Attempt.successful(DecodeResult(xs, rest))
    }
  }

  val idCodec: Codec[String] = list(charClass(isValidIdentifier)).xmap(_.mkString, _.toList)
  def attrCodec: Codec[(String, String)] = until(BitVector('='.toByte), idCodec) ~ idCodec
  def markupCodec: Codec[Markup] =
    constant(BitVector(Y.toByte)) ~> until(BitVector(Y.toByte), idCodec) ~ until(BitVector(X.toByte), listDelimited(BitVector(Y.toByte), attrCodec))

  implicit def textCodec: Codec[Text] = repeat(charClass(isValidText)).xmap(
    chars => Text(chars.mkString),
    _.content.toList
  )

  implicit def elemCodec: Codec[Elem] = lazily {
    ((markupCodec ~ bodyCodec) <~ constant(BitVector(X.toByte, Y.toByte, X.toByte))).xmap(
      { case (markup, body) => Elem(markup, body) },
      { case Elem(markup, body) => (markup, body) }
    )
  }

  def bodyCodec: Codec[Body] = lazily {
    repeat(treeCodec)
  }

  implicit val treeDiscriminated: Discriminated[Tree, Option[Unit]] = Discriminated(optional(constant(BitVector(X.toByte))))
  implicit val elemDiscriminator: Discriminator[Tree, Elem, Option[Unit]] = Discriminator(Some(()))
  implicit val textDiscriminator: Discriminator[Tree, Text, Option[Unit]] = Discriminator(None)

  def treeCodec: Codec[Tree] = Codec.coproduct[Tree].auto

  private def parse_attrib(source: CharSequence) = {
    val s = source.toString
    val i = s.indexOf('=')
    if (i <= 0) sys.error("bad attribute")
    (s.substring(0, i), s.substring(i + 1))
  }

  def fromYXML(source: String): Tree = bodyFromYXML(source) match {
    case List(result) => result
    case Nil => Text("")
    case _ => sys.error("multiple results")
  }

  def bodyToYXML(body: Body): String = {
    val s = new StringBuilder
    def attrib(p: (String, String)) = { s += Y; s ++= p._1; s += '='; s ++= p._2; () }
    def tree(t: Tree): Unit =
      t match {
        case Elem((name, atts), ts) =>
          s += X; s += Y; s ++= name; atts.foreach(attrib); s += X
          ts.foreach(tree)
          s += X; s += Y; s += X
          ()
        case Text(text) =>
          s ++= text
          ()
      }
    body.foreach(tree)
    s.toString
  }

  def bodyFromYXML(source: String): Body = {
    def buffer(): ListBuffer[Tree] = new ListBuffer[Tree]
    var stack: List[(Markup, ListBuffer[Tree])] = List((("", Nil), buffer()))

    def add(x: Tree) = (stack: @unchecked) match {
      case ((_, body) :: _) => body += x; ()
    }

    def push(name: String, atts: List[(String, String)])
    {
      if (name == "") sys.error("bad element")
      else stack = ((name, atts), buffer()) :: stack
    }

    def pop()
    {
      (stack: @unchecked) match {
        case ((("", Nil), _) :: _) => sys.error("unbalanced element")
        case ((markup, body) :: pending) =>
          stack = pending
          add(Elem(markup, body.toList))
      }
    }

    for (chunk <- source.split(X) if chunk.length != 0) {
      if (chunk.length == 1 && chunk.charAt(0) == Y) pop()
      else {
        chunk.split(Y).toList match {
          case ch :: name :: atts if ch.length == 0 =>
            push(name.toString, atts.map(parse_attrib))
          case txts => for (txt <- txts) add(Text(txt.toString))
        }
      }
    }
    (stack: @unchecked) match {
      case List((("", Nil), body)) => body.toList
      case ((name, _), _) :: _ => sys.error("unbalanced element")
    }
  }

}
