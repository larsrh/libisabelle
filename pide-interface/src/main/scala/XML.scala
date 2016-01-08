package edu.tum.cs.isabelle.api

import scala.collection.mutable.ListBuffer

import acyclic.file

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
    def pretty(indent: Int = 0): String
    final def pretty: String = pretty()
  }

  final case class Elem(markup: Markup, body: Body) extends Tree {
    def pretty(indent: Int = 0) = {
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
  }

  final case class Text(content: String) extends Tree {
    def pretty(indent: Int = 0) =
      " " * indent + prettyEscape(content)
  }

  type Body = List[Tree]

  @inline
  def elem(markup: Markup, body: Body): Tree = Elem(markup, body)

  @inline
  def text(content: String): Tree = Text(content)

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

}
