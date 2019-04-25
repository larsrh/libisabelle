/*  Title:      Pure/Isar/parse.scala
    Author:     Makarius

Generic parsers for Isabelle/Isar outer syntax.
*/

package isabelle


import scala.util.parsing.combinator.Parsers
import scala.annotation.tailrec


object Parse
{
  /* parsing tokens */

  trait Parser extends Parsers
  {
    type Elem = Token

    def filter_proper: Boolean = true

    @tailrec private def proper(in: Input): Input =
      if (!filter_proper || in.atEnd || in.first.is_proper) in
      else proper(in.rest)

    private def proper_position: Parser[Position.T] =
      new Parser[Position.T] {
        def apply(raw_input: Input) =
        {
          val in = proper(raw_input)
          val pos =
            in.pos match {
              case pos: Token.Pos => pos
              case _ => Token.Pos.none
            }
          Success(if (in.atEnd) pos.position() else pos.position(in.first), in)
        }
      }

    def position[A](parser: Parser[A]): Parser[(A, Position.T)] =
      proper_position ~ parser ^^ { case x ~ y => (y, x) }

    def token(s: String, pred: Elem => Boolean): Parser[Elem] =
      new Parser[Elem] {
        def apply(raw_input: Input) =
        {
          val in = proper(raw_input)
          if (in.atEnd) Failure(s + " expected,\nbut end-of-input was found", in)
          else {
            val token = in.first
            if (pred(token)) Success(token, proper(in.rest))
            else Failure(s + " expected,\nbut " + token.kind + " was found:\n" + token.source, in)
          }
        }
      }

    def atom(s: String, pred: Elem => Boolean): Parser[String] =
      token(s, pred) ^^ (_.content)

    def command(name: String): Parser[String] = atom("command " + quote(name), _.is_command(name))
    def $$$(name: String): Parser[String] = atom("keyword " + quote(name), _.is_keyword(name))
    def string: Parser[String] = atom("string", _.is_string)
    def nat: Parser[Int] = atom("natural number", _.is_nat) ^^ (s => Integer.parseInt(s))
    def name: Parser[String] = atom("name", _.is_name)
    def embedded: Parser[String] = atom("embedded content", _.is_embedded)
    def text: Parser[String] = atom("text", _.is_text)
    def ML_source: Parser[String] = atom("ML source", _.is_text)
    def document_source: Parser[String] = atom("document source", _.is_text)

    def path: Parser[String] =
      atom("file name/path specification", tok => tok.is_embedded && Path.is_wellformed(tok.content))

    def session_name: Parser[String] = atom("session name", _.is_system_name)
    def theory_name: Parser[String] = atom("theory name", _.is_system_name)

    private def tag_name: Parser[String] =
      atom("tag name", tok =>
          tok.kind == Token.Kind.IDENT ||
          tok.kind == Token.Kind.STRING)

    def tag: Parser[String] = $$$("%") ~> tag_name
    def tags: Parser[List[String]] = rep(tag)

    def marker: Parser[String] = token("marker", _.is_marker) ^^ (_.content)

    def annotation: Parser[Unit] = rep(tag | marker) ^^ { case _ => () }


    /* wrappers */

    def parse[T](p: Parser[T], in: Token.Reader): ParseResult[T] = p(in)

    def parse_all[T](p: Parser[T], in: Token.Reader): ParseResult[T] =
    {
      val result = parse(p, in)
      val rest = proper(result.next)
      if (result.successful && !rest.atEnd) Error("bad input", rest)
      else result
    }
  }
}

