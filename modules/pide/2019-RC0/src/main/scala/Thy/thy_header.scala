/*  Title:      Pure/Thy/thy_header.scala
    Author:     Makarius

Static theory header information.
*/

package isabelle


import scala.annotation.tailrec
import scala.collection.mutable
import scala.util.parsing.input.Reader
import scala.util.matching.Regex


object Thy_Header
{
  /* bootstrap keywords */

  type Keywords = List[(String, Keyword.Spec)]
  type Abbrevs = List[(String, String)]

  val CHAPTER = "chapter"
  val SECTION = "section"
  val SUBSECTION = "subsection"
  val SUBSUBSECTION = "subsubsection"
  val PARAGRAPH = "paragraph"
  val SUBPARAGRAPH = "subparagraph"
  val TEXT = "text"
  val TXT = "txt"
  val TEXT_RAW = "text_raw"

  val THEORY = "theory"
  val IMPORTS = "imports"
  val KEYWORDS = "keywords"
  val ABBREVS = "abbrevs"
  val AND = "and"
  val BEGIN = "begin"

  val bootstrap_header: Keywords =
    List(
      ("%", Keyword.Spec.none),
      ("(", Keyword.Spec.none),
      (")", Keyword.Spec.none),
      (",", Keyword.Spec.none),
      ("::", Keyword.Spec.none),
      ("=", Keyword.Spec.none),
      (AND, Keyword.Spec.none),
      (BEGIN, Keyword.Spec(Keyword.QUASI_COMMAND)),
      (IMPORTS, Keyword.Spec(Keyword.QUASI_COMMAND)),
      (KEYWORDS, Keyword.Spec(Keyword.QUASI_COMMAND)),
      (ABBREVS, Keyword.Spec(Keyword.QUASI_COMMAND)),
      (CHAPTER, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (SECTION, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (SUBSECTION, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (SUBSUBSECTION, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (PARAGRAPH, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (SUBPARAGRAPH, Keyword.Spec(Keyword.DOCUMENT_HEADING)),
      (TEXT, Keyword.Spec(Keyword.DOCUMENT_BODY)),
      (TXT, Keyword.Spec(Keyword.DOCUMENT_BODY)),
      (TEXT_RAW, Keyword.Spec(Keyword.DOCUMENT_RAW)),
      (THEORY, Keyword.Spec(Keyword.THY_BEGIN, tags = List("theory"))),
      ("ML", Keyword.Spec(Keyword.THY_DECL, tags = List("ML"))))

  private val bootstrap_keywords =
    Keyword.Keywords.empty.add_keywords(bootstrap_header)

  val bootstrap_syntax: Outer_Syntax =
    Outer_Syntax.empty.add_keywords(bootstrap_header)


  /* file name vs. theory name */

  val PURE = "Pure"
  val ML_BOOTSTRAP = "ML_Bootstrap"
  val ml_roots = List("ROOT0.ML" -> "ML_Root0", "ROOT.ML" -> "ML_Root")
  val bootstrap_thys = List(PURE, ML_BOOTSTRAP).map(a => a -> ("Bootstrap_" + a))

  val bootstrap_global_theories =
    (Sessions.root_name :: (ml_roots ::: bootstrap_thys).map(_._2)).map(_ -> PURE)

  private val Thy_File_Name = new Regex(""".*?([^/\\:]+)\.thy""")
  private val Split_File_Name = new Regex("""(.*?)[/\\]*([^/\\:]+)""")
  private val File_Name = new Regex(""".*?([^/\\:]+)""")

  def is_base_name(s: String): Boolean =
    s != "" && !s.exists("/\\:".contains(_))

  def split_file_name(s: String): Option[(String, String)] =
    s match {
      case Split_File_Name(s1, s2) => Some((s1, s2))
      case _ => None
    }

  def import_name(s: String): String =
    s match {
      case File_Name(name) if !name.endsWith(".thy") => name
      case _ => error("Malformed theory import: " + quote(s))
    }

  def theory_name(s: String): String =
    s match {
      case Thy_File_Name(name) => bootstrap_name(name)
      case File_Name(name) =>
        if (name == Sessions.root_name) name
        else ml_roots.collectFirst({ case (a, b) if a == name => b }).getOrElse("")
      case _ => ""
    }

  def is_ml_root(theory: String): Boolean =
    ml_roots.exists({ case (_, b) => b == theory })

  def is_bootstrap(theory: String): Boolean =
    bootstrap_thys.exists({ case (_, b) => b == theory })

  def bootstrap_name(theory: String): String =
    bootstrap_thys.collectFirst({ case (a, b) if a == theory => b }).getOrElse(theory)


  /* parser */

  trait Parser extends Parse.Parser
  {
    val header: Parser[Thy_Header] =
    {
      val opt_files =
        $$$("(") ~! (rep1sep(name, $$$(",")) <~ $$$(")")) ^^ { case _ ~ x => x } |
        success(Nil)

      val keyword_spec =
        atom("outer syntax keyword specification", _.is_name) ~ opt_files ~ tags ^^
        { case x ~ y ~ z => Keyword.Spec(x, y, z) }

      val keyword_decl =
        rep1(string) ~
        opt($$$("::") ~! keyword_spec ^^ { case _ ~ x => x }) ^^
        { case xs ~ y => xs.map((_, y.getOrElse(Keyword.Spec.none))) }

      val keyword_decls =
        keyword_decl ~ rep($$$(AND) ~! keyword_decl ^^ { case _ ~ x => x }) ^^
        { case xs ~ yss => (xs :: yss).flatten }

      val abbrevs =
        rep1sep(rep1(text) ~ ($$$("=") ~! rep1(text)), $$$("and")) ^^
          { case res => for ((as ~ (_ ~ bs)) <- res; a <- as; b <- bs) yield (a, b) }

      val args =
        position(theory_name) ~
        (opt($$$(IMPORTS) ~! rep1(position(theory_name))) ^^
          { case None => Nil case Some(_ ~ xs) => xs }) ~
        (opt($$$(KEYWORDS) ~! keyword_decls) ^^
          { case None => Nil case Some(_ ~ xs) => xs }) ~
        (opt($$$(ABBREVS) ~! abbrevs) ^^
          { case None => Nil case Some(_ ~ xs) => xs }) ~
        $$$(BEGIN) ^^
        { case (name, pos) ~ imports ~ keywords ~ abbrevs ~ _ =>
            val f = Symbol.decode _
            Thy_Header((f(name), pos),
              imports.map({ case (a, b) => (f(a), b) }),
              keywords.map({ case (a, Keyword.Spec(b, c, d)) =>
                (f(a), Keyword.Spec(f(b), c.map(f), d.map(f))) }),
              abbrevs.map({ case (a, b) => (f(a), f(b)) }))
        }

      val heading =
        (command(CHAPTER) |
          command(SECTION) |
          command(SUBSECTION) |
          command(SUBSUBSECTION) |
          command(PARAGRAPH) |
          command(SUBPARAGRAPH) |
          command(TEXT) |
          command(TXT) |
          command(TEXT_RAW)) ~
        annotation ~! document_source

      (rep(heading) ~ command(THEORY) ~ annotation) ~! args ^^ { case _ ~ x => x }
    }
  }


  /* read -- lazy scanning */

  private def read_tokens(reader: Reader[Char], strict: Boolean): (List[Token], List[Token]) =
  {
    val token = Token.Parsers.token(bootstrap_keywords)
    def make_tokens(in: Reader[Char]): Stream[Token] =
      token(in) match {
        case Token.Parsers.Success(tok, rest) => tok #:: make_tokens(rest)
        case _ => Stream.empty
      }

    val all_tokens = make_tokens(reader)
    val drop_tokens =
      if (strict) Nil
      else all_tokens.takeWhile(tok => !tok.is_command(Thy_Header.THEORY)).toList

    val tokens = all_tokens.drop(drop_tokens.length)
    val tokens1 = tokens.takeWhile(tok => !tok.is_begin).toList
    val tokens2 = tokens.dropWhile(tok => !tok.is_begin).headOption.toList

    (drop_tokens, tokens1 ::: tokens2)
  }

  private object Parser extends Parser
  {
    def parse_header(tokens: List[Token], pos: Token.Pos): Thy_Header =
      parse(commit(header), Token.reader(tokens, pos)) match {
        case Success(result, _) => result
        case bad => error(bad.toString)
      }
  }

  def read(reader: Reader[Char], start: Token.Pos, strict: Boolean = true): Thy_Header =
  {
    val (_, tokens0) = read_tokens(reader, true)
    val text = Scan.reader_decode_utf8(reader, Token.implode(tokens0))

    val (drop_tokens, tokens) = read_tokens(Scan.char_reader(text), strict)
    val pos = (start /: drop_tokens)(_.advance(_))

    Parser.parse_header(tokens, pos)
  }
}

sealed case class Thy_Header(
  name: (String, Position.T),
  imports: List[(String, Position.T)],
  keywords: Thy_Header.Keywords,
  abbrevs: Thy_Header.Abbrevs)
