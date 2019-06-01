/*  Title:      Pure/General/symbol.scala
    Author:     Makarius

Isabelle text symbols.
*/

package isabelle


import scala.collection.mutable
import scala.util.matching.Regex
import scala.annotation.tailrec


object Symbol
{
  type Symbol = String

  // counting Isabelle symbols, starting from 1
  type Offset = Text.Offset
  type Range = Text.Range


  /* spaces */

  val space = " "

  private val static_spaces = space * 4000

  def spaces(n: Int): String =
  {
    require(n >= 0)
    if (n < static_spaces.length) static_spaces.substring(0, n)
    else space * n
  }


  /* ASCII characters */

  def is_ascii_letter(c: Char): Boolean = 'A' <= c && c <= 'Z' || 'a' <= c && c <= 'z'

  def is_ascii_digit(c: Char): Boolean = '0' <= c && c <= '9'

  def is_ascii_hex(c: Char): Boolean =
    '0' <= c && c <= '9' || 'A' <= c && c <= 'F' || 'a' <= c && c <= 'f'

  def is_ascii_quasi(c: Char): Boolean = c == '_' || c == '\''

  def is_ascii_blank(c: Char): Boolean = " \t\n\u000b\f\r".contains(c)

  def is_ascii_line_terminator(c: Char): Boolean = "\r\n".contains(c)

  def is_ascii_letdig(c: Char): Boolean =
    is_ascii_letter(c) || is_ascii_digit(c) || is_ascii_quasi(c)

  def is_ascii_identifier(s: String): Boolean =
    s.length > 0 && is_ascii_letter(s(0)) && s.forall(is_ascii_letdig)

  def ascii(c: Char): Symbol =
  {
    if (c > 127) error("Non-ASCII character: " + quote(c.toString))
    else char_symbols(c.toInt)
  }

  def is_ascii(s: Symbol): Boolean = s.length == 1 && s(0) < 128


  /* symbol matching */

  private val symbol_total = new Regex("""(?xs)
    [\ud800-\udbff][\udc00-\udfff] | \r\n | \\ < \^? ([A-Za-z][A-Za-z0-9_']*)? >? | .""")

  private def is_plain(c: Char): Boolean =
    !(c == '\r' || c == '\\' || Character.isHighSurrogate(c))

  def is_malformed(s: Symbol): Boolean =
    s.length match {
      case 1 =>
        val c = s(0)
        Character.isHighSurrogate(c) || Character.isLowSurrogate(c) || c == '\ufffd'
      case 2 =>
        val c1 = s(0)
        val c2 = s(1)
        !(c1 == '\r' && c2 == '\n' || Character.isSurrogatePair(c1, c2))
      case _ => !s.endsWith(">") || s == "\\<>" || s == "\\<^>"
    }

  def is_newline(s: Symbol): Boolean =
    s == "\n" || s == "\r" || s == "\r\n"

  class Matcher(text: CharSequence)
  {
    private val matcher = symbol_total.pattern.matcher(text)
    def apply(start: Int, end: Int): Int =
    {
      require(0 <= start && start < end && end <= text.length)
      if (is_plain(text.charAt(start))) 1
      else {
        matcher.region(start, end).lookingAt
        matcher.group.length
      }
    }
  }


  /* iterator */

  private val char_symbols: Array[Symbol] =
    (0 until 256).iterator.map(i => new String(Array(i.toChar))).toArray

  def iterator(text: CharSequence): Iterator[Symbol] =
    new Iterator[Symbol]
    {
      private val matcher = new Matcher(text)
      private var i = 0
      def hasNext = i < text.length
      def next =
      {
        val n = matcher(i, text.length)
        val s =
          if (n == 0) ""
          else if (n == 1) {
            val c = text.charAt(i)
            if (c < char_symbols.length) char_symbols(c)
            else text.subSequence(i, i + n).toString
          }
          else text.subSequence(i, i + n).toString
        i += n
        s
      }
    }

  def explode(text: CharSequence): List[Symbol] = iterator(text).toList

  def length(text: CharSequence): Int = iterator(text).length

  def trim_blanks(text: CharSequence): String =
    Library.trim(is_blank(_: Symbol), explode(text)).mkString

  def all_blank(str: String): Boolean =
    iterator(str).forall(is_blank(_))

  def trim_blank_lines(text: String): String =
    cat_lines(split_lines(text).dropWhile(all_blank).reverse.dropWhile(all_blank).reverse)


  /* decoding offsets */

  object Index
  {
    private sealed case class Entry(chr: Int, sym: Int)

    val empty: Index = new Index(Nil)

    def apply(text: CharSequence): Index =
    {
      val matcher = new Matcher(text)
      val buf = new mutable.ListBuffer[Entry]
      var chr = 0
      var sym = 0
      while (chr < text.length) {
        val n = matcher(chr, text.length)
        chr += n
        sym += 1
        if (n > 1) buf += Entry(chr, sym)
      }
      if (buf.isEmpty) empty else new Index(buf.toList)
    }
  }

  final class Index private(entries: List[Index.Entry])
  {
    private val hash: Int = entries.hashCode
    private val index: Array[Index.Entry] = entries.toArray

    def decode(symbol_offset: Offset): Text.Offset =
    {
      val sym = symbol_offset - 1
      val end = index.length
      @tailrec def bisect(a: Int, b: Int): Int =
      {
        if (a < b) {
          val c = (a + b) / 2
          if (sym < index(c).sym) bisect(a, c)
          else if (c + 1 == end || sym < index(c + 1).sym) c
          else bisect(c + 1, b)
        }
        else -1
      }
      val i = bisect(0, end)
      if (i < 0) sym
      else index(i).chr + sym - index(i).sym
    }
    def decode(symbol_range: Range): Text.Range = symbol_range.map(decode(_))

    override def hashCode: Int = hash
    override def equals(that: Any): Boolean =
      that match {
        case other: Index => index.sameElements(other.index)
        case _ => false
      }
  }


  /* symbolic text chunks -- without actual text */

  object Text_Chunk
  {
    sealed abstract class Name
    case object Default extends Name
    case class Id(id: Document_ID.Generic) extends Name
    case class File(name: String) extends Name

    val encode_name: XML.Encode.T[Name] =
    {
      import XML.Encode._
      variant(List(
        { case Default => (Nil, Nil) },
        { case Id(a) => (List(long_atom(a)), Nil) },
        { case File(a) => (List(a), Nil) }))
    }

    val decode_name: XML.Decode.T[Name] =
    {
      import XML.Decode._
      variant(List(
        { case (Nil, Nil) => Default },
        { case (List(a), Nil) => Id(long_atom(a)) },
        { case (List(a), Nil) => File(a) }))
    }

    def apply(text: CharSequence): Text_Chunk =
      new Text_Chunk(Text.Range(0, text.length), Index(text))
  }

  final class Text_Chunk private(val range: Text.Range, private val index: Index)
  {
    override def hashCode: Int = (range, index).hashCode
    override def equals(that: Any): Boolean =
      that match {
        case other: Text_Chunk =>
          range == other.range &&
          index == other.index
        case _ => false
      }

    override def toString: String = "Text_Chunk" + range.toString

    def decode(symbol_offset: Offset): Text.Offset = index.decode(symbol_offset)
    def decode(symbol_range: Range): Text.Range = index.decode(symbol_range)
    def incorporate(symbol_range: Range): Option[Text.Range] =
    {
      def in(r: Range): Option[Text.Range] =
        range.try_restrict(decode(r)) match {
          case Some(r1) if !r1.is_singularity => Some(r1)
          case _ => None
        }
     in(symbol_range) orElse in(symbol_range - 1)
    }
  }


  /* recoding text */

  private class Recoder(list: List[(String, String)])
  {
    private val (min, max) =
    {
      var min = '\uffff'
      var max = '\u0000'
      for ((x, _) <- list) {
        val c = x(0)
        if (c < min) min = c
        if (c > max) max = c
      }
      (min, max)
    }
    private val table =
    {
      var tab = Map[String, String]()
      for ((x, y) <- list) {
        tab.get(x) match {
          case None => tab += (x -> y)
          case Some(z) =>
            error("Duplicate symbol mapping of " + quote(x) + " to " + quote(y) + " vs. " + quote(z))
        }
      }
      tab
    }
    def recode(text: String): String =
    {
      val len = text.length
      val matcher = symbol_total.pattern.matcher(text)
      val result = new StringBuilder(len)
      var i = 0
      while (i < len) {
        val c = text(i)
        if (min <= c && c <= max) {
          matcher.region(i, len).lookingAt
          val x = matcher.group
          result.append(table.getOrElse(x, x))
          i = matcher.end
        }
        else { result.append(c); i += 1 }
      }
      result.toString
    }
  }



  /** symbol interpretation **/

  val ARGUMENT_CARTOUCHE = "cartouche"
  val ARGUMENT_SPACE_CARTOUCHE = "space_cartouche"

  private lazy val symbols =
  {
    val contents =
      for (path <- Path.split(Isabelle_System.getenv("ISABELLE_SYMBOLS")) if path.is_file)
        yield (File.read(path))
    new Interpretation(cat_lines(contents))
  }

  private class Interpretation(symbols_spec: String)
  {
    /* read symbols */

    private val No_Decl = new Regex("""(?xs) ^\s* (?: \#.* )? $ """)
    private val Key = new Regex("""(?xs) (.+): """)

    private def read_decl(decl: String): (Symbol, Properties.T) =
    {
      def err() = error("Bad symbol declaration: " + decl)

      def read_props(props: List[String]): Properties.T =
      {
        props match {
          case Nil => Nil
          case _ :: Nil => err()
          case Key(x) :: y :: rest => (x -> y.replace('\u2423', ' ')) :: read_props(rest)
          case _ => err()
        }
      }
      decl.split("\\s+").toList match {
        case sym :: props if sym.length > 1 && !is_malformed(sym) =>
          (sym, read_props(props))
        case _ => err()
      }
    }

    private val symbols: List[(Symbol, Properties.T)] =
      (((List.empty[(Symbol, Properties.T)], Set.empty[Symbol]) /:
          split_lines(symbols_spec).reverse)
        { case (res, No_Decl()) => res
          case ((list, known), decl) =>
            val (sym, props) = read_decl(decl)
            if (known(sym)) (list, known)
            else ((sym, props) :: list, known + sym)
        })._1


    /* basic properties */

    val properties: Map[Symbol, Properties.T] = Map(symbols: _*)

    val names: Map[Symbol, (String, String)] =
    {
      val Name = new Regex("""\\<\^?([A-Za-z][A-Za-z0-9_']*)>""")
      val Argument = new Properties.String("argument")
      def argument(sym: Symbol, props: Properties.T): String =
        props match {
          case Argument(arg) =>
            if (arg == ARGUMENT_CARTOUCHE || arg == ARGUMENT_SPACE_CARTOUCHE) arg
            else error("Bad argument: " + quote(arg) + " for symbol " + quote(sym))
          case _ => ""
        }
      Map((for ((sym @ Name(a), props) <- symbols) yield sym -> (a, argument(sym, props))): _*)
    }

    val groups: List[(String, List[Symbol])] =
      symbols.map({ case (sym, props) =>
        val gs = for (("group", g) <- props) yield g
        if (gs.isEmpty) List(sym -> "unsorted") else gs.map(sym -> _)
      }).flatten
        .groupBy(_._2).toList.map({ case (group, list) => (group, list.map(_._1)) })
        .sortBy(_._1)

    val abbrevs: Multi_Map[Symbol, String] =
      Multi_Map((
        for {
          (sym, props) <- symbols
          ("abbrev", a) <- props.reverse
        } yield sym -> a): _*)

    val codes: List[(Symbol, Int)] =
    {
      val Code = new Properties.String("code")
      for {
        (sym, props) <- symbols
        code <-
          props match {
            case Code(s) =>
              try { Some(Integer.decode(s).intValue) }
              catch { case _: NumberFormatException => error("Bad code for symbol " + sym) }
            case _ => None
          }
      } yield {
        if (code < 128) error("Illegal ASCII code for symbol " + sym)
        else (sym, code)
      }
    }


    /* recoding */

    private val (decoder, encoder) =
    {
      val mapping =
        for ((sym, code) <- codes) yield (sym, new String(Character.toChars(code)))
      (new Recoder(mapping), new Recoder(for ((x, y) <- mapping) yield (y, x)))
    }

    def decode(text: String): String = decoder.recode(text)
    def encode(text: String): String = encoder.recode(text)

    private def recode_set(elems: String*): Set[String] =
    {
      val content = elems.toList
      Set((content ::: content.map(decode)): _*)
    }

    private def recode_map[A](elems: (String, A)*): Map[String, A] =
    {
      val content = elems.toList
      Map((content ::: content.map({ case (sym, a) => (decode(sym), a) })): _*)
    }


    /* user fonts */

    private val Font = new Properties.String("font")
    val fonts: Map[Symbol, String] =
      recode_map((for ((sym, Font(font)) <- symbols) yield sym -> font): _*)

    val font_names: List[String] = Set(fonts.toList.map(_._2): _*).toList
    val font_index: Map[String, Int] = Map((font_names zip (0 until font_names.length).toList): _*)


    /* classification */

    val letters = recode_set(
      "A", "B", "C", "D", "E", "F", "G", "H", "I", "J", "K", "L", "M",
      "N", "O", "P", "Q", "R", "S", "T", "U", "V", "W", "X", "Y", "Z",
      "a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l", "m",
      "n", "o", "p", "q", "r", "s", "t", "u", "v", "w", "x", "y", "z",

      "\\<A>", "\\<B>", "\\<C>", "\\<D>", "\\<E>", "\\<F>", "\\<G>",
      "\\<H>", "\\<I>", "\\<J>", "\\<K>", "\\<L>", "\\<M>", "\\<N>",
      "\\<O>", "\\<P>", "\\<Q>", "\\<R>", "\\<S>", "\\<T>", "\\<U>",
      "\\<V>", "\\<W>", "\\<X>", "\\<Y>", "\\<Z>", "\\<a>", "\\<b>",
      "\\<c>", "\\<d>", "\\<e>", "\\<f>", "\\<g>", "\\<h>", "\\<i>",
      "\\<j>", "\\<k>", "\\<l>", "\\<m>", "\\<n>", "\\<o>", "\\<p>",
      "\\<q>", "\\<r>", "\\<s>", "\\<t>", "\\<u>", "\\<v>", "\\<w>",
      "\\<x>", "\\<y>", "\\<z>",

      "\\<AA>", "\\<BB>", "\\<CC>", "\\<DD>", "\\<EE>", "\\<FF>",
      "\\<GG>", "\\<HH>", "\\<II>", "\\<JJ>", "\\<KK>", "\\<LL>",
      "\\<MM>", "\\<NN>", "\\<OO>", "\\<PP>", "\\<QQ>", "\\<RR>",
      "\\<SS>", "\\<TT>", "\\<UU>", "\\<VV>", "\\<WW>", "\\<XX>",
      "\\<YY>", "\\<ZZ>", "\\<aa>", "\\<bb>", "\\<cc>", "\\<dd>",
      "\\<ee>", "\\<ff>", "\\<gg>", "\\<hh>", "\\<ii>", "\\<jj>",
      "\\<kk>", "\\<ll>", "\\<mm>", "\\<nn>", "\\<oo>", "\\<pp>",
      "\\<qq>", "\\<rr>", "\\<ss>", "\\<tt>", "\\<uu>", "\\<vv>",
      "\\<ww>", "\\<xx>", "\\<yy>", "\\<zz>",

      "\\<alpha>", "\\<beta>", "\\<gamma>", "\\<delta>", "\\<epsilon>",
      "\\<zeta>", "\\<eta>", "\\<theta>", "\\<iota>", "\\<kappa>",
      "\\<mu>", "\\<nu>", "\\<xi>", "\\<pi>", "\\<rho>", "\\<sigma>",
      "\\<tau>", "\\<upsilon>", "\\<phi>", "\\<chi>", "\\<psi>",
      "\\<omega>", "\\<Gamma>", "\\<Delta>", "\\<Theta>", "\\<Lambda>",
      "\\<Xi>", "\\<Pi>", "\\<Sigma>", "\\<Upsilon>", "\\<Phi>",
      "\\<Psi>", "\\<Omega>")

    val blanks = recode_set(space, "\t", "\n", "\u000B", "\f", "\r", "\r\n")

    val sym_chars =
      Set("!", "#", "$", "%", "&", "*", "+", "-", "/", "<", "=", ">", "?", "@", "^", "_", "|", "~")

    val symbolic = recode_set((for { (sym, _) <- symbols; if raw_symbolic(sym) } yield sym): _*)


    /* misc symbols */

    val newline_decoded = decode(newline)
    val comment_decoded = decode(comment)
    val cancel_decoded = decode(cancel)
    val latex_decoded = decode(latex)
    val marker_decoded = decode(marker)
    val open_decoded = decode(open)
    val close_decoded = decode(close)


    /* control symbols */

    val control_decoded: Set[Symbol] =
      Set((for ((sym, _) <- symbols if sym.startsWith("\\<^")) yield decode(sym)): _*)

    val sub_decoded = decode(sub)
    val sup_decoded = decode(sup)
    val bold_decoded = decode(bold)
    val emph_decoded = decode(emph)
    val bsub_decoded = decode(bsub)
    val esub_decoded = decode(esub)
    val bsup_decoded = decode(bsup)
    val esup_decoded = decode(esup)
  }


  /* tables */

  def properties: Map[Symbol, Properties.T] = symbols.properties
  def names: Map[Symbol, (String, String)] = symbols.names
  def groups: List[(String, List[Symbol])] = symbols.groups
  def abbrevs: Multi_Map[Symbol, String] = symbols.abbrevs
  def codes: List[(Symbol, Int)] = symbols.codes
  def groups_code: List[(String, List[Symbol])] =
  {
    val has_code = codes.iterator.map(_._1).toSet
    groups.flatMap({ case (group, symbols) =>
      val symbols1 = symbols.filter(has_code)
      if (symbols1.isEmpty) None else Some((group, symbols1))
    })
  }

  lazy val is_code: Int => Boolean = codes.map(_._2).toSet
  def decode(text: String): String = symbols.decode(text)
  def encode(text: String): String = symbols.encode(text)

  def decode_yxml(text: String): XML.Body = YXML.parse_body(decode(text))
  def decode_yxml_failsafe(text: String): XML.Body = YXML.parse_body_failsafe(decode(text))
  def encode_yxml(body: XML.Body): String = encode(YXML.string_of_body(body))

  def decode_strict(text: String): String =
  {
    val decoded = decode(text)
    if (encode(decoded) == text) decoded
    else {
      val bad = new mutable.ListBuffer[Symbol]
      for (s <- iterator(text) if encode(decode(s)) != s && !bad.contains(s))
        bad += s
      error("Bad Unicode symbols in text: " + commas_quote(bad))
    }
  }

  def fonts: Map[Symbol, String] = symbols.fonts
  def font_names: List[String] = symbols.font_names
  def font_index: Map[String, Int] = symbols.font_index
  def lookup_font(sym: Symbol): Option[Int] = symbols.fonts.get(sym).map(font_index(_))


  /* classification */

  def is_letter(sym: Symbol): Boolean = symbols.letters.contains(sym)
  def is_digit(sym: Symbol): Boolean = sym.length == 1 && '0' <= sym(0) && sym(0) <= '9'
  def is_quasi(sym: Symbol): Boolean = sym == "_" || sym == "'"
  def is_letdig(sym: Symbol): Boolean = is_letter(sym) || is_digit(sym) || is_quasi(sym)
  def is_blank(sym: Symbol): Boolean = symbols.blanks.contains(sym)


  /* symbolic newline */

  val newline: Symbol = "\\<newline>"
  def newline_decoded: Symbol = symbols.newline_decoded

  def print_newlines(str: String): String =
    if (str.contains('\n'))
      (for (s <- iterator(str)) yield { if (s == "\n") newline_decoded else s }).mkString
    else str


  /* formal comments */

  val comment: Symbol = "\\<comment>"
  val cancel: Symbol = "\\<^cancel>"
  val latex: Symbol = "\\<^latex>"
  val marker: Symbol = "\\<^marker>"

  def comment_decoded: Symbol = symbols.comment_decoded
  def cancel_decoded: Symbol = symbols.cancel_decoded
  def latex_decoded: Symbol = symbols.latex_decoded
  def marker_decoded: Symbol = symbols.marker_decoded


  /* cartouches */

  val open: Symbol = "\\<open>"
  val close: Symbol = "\\<close>"

  def open_decoded: Symbol = symbols.open_decoded
  def close_decoded: Symbol = symbols.close_decoded

  def is_open(sym: Symbol): Boolean = sym == open_decoded || sym == open
  def is_close(sym: Symbol): Boolean = sym == close_decoded || sym == close

  def cartouche(s: String): String = open + s + close
  def cartouche_decoded(s: String): String = open_decoded + s + close_decoded


  /* symbols for symbolic identifiers */

  private def raw_symbolic(sym: Symbol): Boolean =
    sym.startsWith("\\<") && sym.endsWith(">") && !sym.startsWith("\\<^")

  def is_symbolic(sym: Symbol): Boolean =
    !is_open(sym) && !is_close(sym) && (raw_symbolic(sym) || symbols.symbolic.contains(sym))

  def is_symbolic_char(sym: Symbol): Boolean = symbols.sym_chars.contains(sym)


  /* control symbols */

  val control_prefix = "\\<^"
  val control_suffix = ">"

  def control_name(sym: Symbol): Option[String] =
    if (is_control_encoded(sym))
      Some(sym.substring(control_prefix.length, sym.length - control_suffix.length))
    else None

  def is_control_encoded(sym: Symbol): Boolean =
    sym.startsWith(control_prefix) && sym.endsWith(control_suffix)

  def is_control(sym: Symbol): Boolean =
    is_control_encoded(sym) || symbols.control_decoded.contains(sym)

  def is_controllable(sym: Symbol): Boolean =
    !is_blank(sym) && !is_control(sym) && !is_open(sym) && !is_close(sym) &&
    !is_malformed(sym) && sym != "\""

  val sub = "\\<^sub>"
  val sup = "\\<^sup>"
  val bold = "\\<^bold>"
  val emph = "\\<^emph>"
  val bsub = "\\<^bsub>"
  val esub = "\\<^esub>"
  val bsup = "\\<^bsup>"
  val esup = "\\<^esup>"

  def sub_decoded: Symbol = symbols.sub_decoded
  def sup_decoded: Symbol = symbols.sup_decoded
  def bold_decoded: Symbol = symbols.bold_decoded
  def emph_decoded: Symbol = symbols.emph_decoded
  def bsub_decoded: Symbol = symbols.bsub_decoded
  def esub_decoded: Symbol = symbols.esub_decoded
  def bsup_decoded: Symbol = symbols.bsup_decoded
  def esup_decoded: Symbol = symbols.esup_decoded
}
