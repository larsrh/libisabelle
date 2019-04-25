/*  Title:      Pure/System/options.scala
    Author:     Makarius

System options with external string representation.
*/

package isabelle


object Options
{
  type Spec = (String, Option[String])

  val empty: Options = new Options()


  /* representation */

  sealed abstract class Type
  {
    def print: String = Word.lowercase(toString)
  }
  case object Bool extends Type
  case object Int extends Type
  case object Real extends Type
  case object String extends Type
  case object Unknown extends Type

  case class Opt(
    public: Boolean,
    pos: Position.T,
    name: String,
    typ: Type,
    value: String,
    default_value: String,
    description: String,
    section: String)
  {
    private def print(default: Boolean): String =
    {
      val x = if (default) default_value else value
      "option " + name + " : " + typ.print + " = " +
        (if (typ == Options.String) quote(x) else x) +
        (if (description == "") "" else "\n  -- " + quote(description))
    }

    def print: String = print(false)
    def print_default: String = print(true)

    def title(strip: String = ""): String =
    {
      val words = Word.explode('_', name)
      val words1 =
        words match {
          case word :: rest if word == strip => rest
          case _ => words
        }
      Word.implode(words1.map(Word.perhaps_capitalize(_)))
    }

    def unknown: Boolean = typ == Unknown
  }


  /* parsing */

  private val SECTION = "section"
  private val PUBLIC = "public"
  private val OPTION = "option"
  private val OPTIONS = Path.explode("etc/options")
  private val PREFS = Path.explode("$ISABELLE_HOME_USER/etc/preferences")

  val options_syntax =
    Outer_Syntax.empty + ":" + "=" + "--" + Symbol.comment + Symbol.comment_decoded +
      (SECTION, Keyword.DOCUMENT_HEADING) +
      (PUBLIC, Keyword.BEFORE_COMMAND) +
      (OPTION, Keyword.THY_DECL)

  val prefs_syntax = Outer_Syntax.empty + "="

  trait Parser extends Parse.Parser
  {
    val option_name = atom("option name", _.is_name)
    val option_type = atom("option type", _.is_name)
    val option_value =
      opt(token("-", tok => tok.is_sym_ident && tok.content == "-")) ~ atom("nat", _.is_nat) ^^
        { case s ~ n => if (s.isDefined) "-" + n else n } |
      atom("option value", tok => tok.is_name || tok.is_float)
  }

  private object Parser extends Parser
  {
    def comment_marker: Parser[String] =
      $$$("--") | $$$(Symbol.comment) | $$$(Symbol.comment_decoded)

    val option_entry: Parser[Options => Options] =
    {
      command(SECTION) ~! text ^^
        { case _ ~ a => (options: Options) => options.set_section(a) } |
      opt($$$(PUBLIC)) ~ command(OPTION) ~! (position(option_name) ~ $$$(":") ~ option_type ~
      $$$("=") ~ option_value ~ (comment_marker ~! text ^^ { case _ ~ x => x } | success(""))) ^^
        { case a ~ _ ~ ((b, pos) ~ _ ~ c ~ _ ~ d ~ e) =>
            (options: Options) => options.declare(a.isDefined, pos, b, c, d, e) }
    }

    val prefs_entry: Parser[Options => Options] =
    {
      option_name ~ ($$$("=") ~! option_value) ^^
      { case a ~ (_ ~ b) => (options: Options) => options.add_permissive(a, b) }
    }

    def parse_file(options: Options, file_name: String, content: String,
      syntax: Outer_Syntax = options_syntax,
      parser: Parser[Options => Options] = option_entry): Options =
    {
      val toks = Token.explode(syntax.keywords, content)
      val ops =
        parse_all(rep(parser), Token.reader(toks, Token.Pos.file(file_name))) match {
          case Success(result, _) => result
          case bad => error(bad.toString)
        }
      try { (options.set_section("") /: ops) { case (opts, op) => op(opts) } }
      catch { case ERROR(msg) => error(msg + Position.File(file_name)) }
    }

    def parse_prefs(options: Options, content: String): Options =
      parse_file(options, PREFS.file_name, content, syntax = prefs_syntax, parser = prefs_entry)
  }

  def read_prefs(file: Path = PREFS): String =
    if (file.is_file) File.read(file) else ""

  def init(prefs: String = read_prefs(PREFS), opts: List[String] = Nil): Options =
  {
    var options = empty
    for {
      dir <- Isabelle_System.components()
      file = dir + OPTIONS if file.is_file
    } { options = Parser.parse_file(options, file.implode, File.read(file)) }
    (Options.Parser.parse_prefs(options, prefs) /: opts)(_ + _)
  }


  /* encode */

  val encode: XML.Encode.T[Options] = (options => options.encode)


  /* Isabelle tool wrapper */

  val isabelle_tool = Isabelle_Tool("options", "print Isabelle system options", args =>
  {
    var build_options = false
    var get_option = ""
    var list_options = false
    var export_file = ""

    val getopts = Getopts("""
Usage: isabelle options [OPTIONS] [MORE_OPTIONS ...]

  Options are:
    -b           include $ISABELLE_BUILD_OPTIONS
    -g OPTION    get value of OPTION
    -l           list options
    -x FILE      export options to FILE in YXML format

  Report Isabelle system options, augmented by MORE_OPTIONS given as
  arguments NAME=VAL or NAME.
""",
      "b" -> (_ => build_options = true),
      "g:" -> (arg => get_option = arg),
      "l" -> (_ => list_options = true),
      "x:" -> (arg => export_file = arg))

    val more_options = getopts(args)
    if (get_option == "" && !list_options && export_file == "") getopts.usage()

    val options =
    {
      val options0 = Options.init()
      val options1 =
        if (build_options)
          (options0 /: Word.explode(Isabelle_System.getenv("ISABELLE_BUILD_OPTIONS")))(_ + _)
        else options0
      (options1 /: more_options)(_ + _)
    }

    if (get_option != "")
      Output.writeln(options.check_name(get_option).value, stdout = true)

    if (export_file != "")
      File.write(Path.explode(export_file), YXML.string_of_body(options.encode))

    if (get_option == "" && export_file == "")
      Output.writeln(options.print, stdout = true)
  })
}


final class Options private(
  val options: Map[String, Options.Opt] = Map.empty,
  val section: String = "")
{
  override def toString: String = options.iterator.mkString("Options(", ",", ")")

  private def print_opt(opt: Options.Opt): String =
    if (opt.public) "public " + opt.print else opt.print

  def print: String = cat_lines(options.toList.sortBy(_._1).map(p => print_opt(p._2)))

  def description(name: String): String = check_name(name).description


  /* check */

  def check_name(name: String): Options.Opt =
    options.get(name) match {
      case Some(opt) if !opt.unknown => opt
      case _ => error("Unknown option " + quote(name))
    }

  private def check_type(name: String, typ: Options.Type): Options.Opt =
  {
    val opt = check_name(name)
    if (opt.typ == typ) opt
    else error("Ill-typed option " + quote(name) + " : " + opt.typ.print + " vs. " + typ.print)
  }


  /* basic operations */

  private def put[A](name: String, typ: Options.Type, value: String): Options =
  {
    val opt = check_type(name, typ)
    new Options(options + (name -> opt.copy(value = value)), section)
  }

  private def get[A](name: String, typ: Options.Type, parse: String => Option[A]): A =
  {
    val opt = check_type(name, typ)
    parse(opt.value) match {
      case Some(x) => x
      case None =>
        error("Malformed value for option " + quote(name) +
          " : " + typ.print + " =\n" + quote(opt.value))
    }
  }


  /* internal lookup and update */

  class Bool_Access
  {
    def apply(name: String): Boolean = get(name, Options.Bool, Value.Boolean.unapply)
    def update(name: String, x: Boolean): Options =
      put(name, Options.Bool, Value.Boolean(x))
  }
  val bool = new Bool_Access

  class Int_Access
  {
    def apply(name: String): Int = get(name, Options.Int, Value.Int.unapply)
    def update(name: String, x: Int): Options =
      put(name, Options.Int, Value.Int(x))
  }
  val int = new Int_Access

  class Real_Access
  {
    def apply(name: String): Double = get(name, Options.Real, Value.Double.unapply)
    def update(name: String, x: Double): Options =
      put(name, Options.Real, Value.Double(x))
  }
  val real = new Real_Access

  class String_Access
  {
    def apply(name: String): String = get(name, Options.String, s => Some(s))
    def update(name: String, x: String): Options = put(name, Options.String, x)
  }
  val string = new String_Access

  def proper_string(name: String): Option[String] =
    Library.proper_string(string(name))

  def seconds(name: String): Time = Time.seconds(real(name))


  /* external updates */

  private def check_value(name: String): Options =
  {
    val opt = check_name(name)
    opt.typ match {
      case Options.Bool => bool(name); this
      case Options.Int => int(name); this
      case Options.Real => real(name); this
      case Options.String => string(name); this
      case Options.Unknown => this
    }
  }

  def declare(
    public: Boolean,
    pos: Position.T,
    name: String,
    typ_name: String,
    value: String,
    description: String): Options =
  {
    options.get(name) match {
      case Some(other) =>
        error("Duplicate declaration of option " + quote(name) + Position.here(pos) +
          Position.here(other.pos))
      case None =>
        val typ =
          typ_name match {
            case "bool" => Options.Bool
            case "int" => Options.Int
            case "real" => Options.Real
            case "string" => Options.String
            case _ =>
              error("Unknown type for option " + quote(name) + " : " + quote(typ_name) +
                Position.here(pos))
          }
        val opt = Options.Opt(public, pos, name, typ, value, value, description, section)
        (new Options(options + (name -> opt), section)).check_value(name)
    }
  }

  def add_permissive(name: String, value: String): Options =
  {
    if (options.isDefinedAt(name)) this + (name, value)
    else {
      val opt = Options.Opt(false, Position.none, name, Options.Unknown, value, value, "", "")
      new Options(options + (name -> opt), section)
    }
  }

  def + (name: String, value: String): Options =
  {
    val opt = check_name(name)
    (new Options(options + (name -> opt.copy(value = value)), section)).check_value(name)
  }

  def + (name: String, opt_value: Option[String]): Options =
  {
    val opt = check_name(name)
    opt_value match {
      case Some(value) => this + (name, value)
      case None if opt.typ == Options.Bool => this + (name, "true")
      case None => error("Missing value for option " + quote(name) + " : " + opt.typ.print)
    }
  }

  def + (str: String): Options =
  {
    str.indexOf('=') match {
      case -1 => this + (str, None)
      case i => this + (str.substring(0, i), str.substring(i + 1))
    }
  }

  def ++ (specs: List[Options.Spec]): Options =
    (this /: specs)({ case (x, (y, z)) => x + (y, z) })


  /* sections */

  def set_section(new_section: String): Options =
    new Options(options, new_section)

  def sections: List[(String, List[Options.Opt])] =
    options.groupBy(_._2.section).toList.map({ case (a, opts) => (a, opts.toList.map(_._2)) })


  /* encode */

  def encode: XML.Body =
  {
    val opts =
      for ((_, opt) <- options.toList; if !opt.unknown)
        yield (opt.pos, (opt.name, (opt.typ.print, opt.value)))

    import XML.Encode.{string => string_, _}
    list(pair(properties, pair(string_, pair(string_, string_))))(opts)
  }


  /* save preferences */

  def save_prefs(file: Path = Options.PREFS)
  {
    val defaults: Options = Options.init(prefs = "")
    val changed =
      (for {
        (name, opt2) <- options.iterator
        opt1 = defaults.options.get(name)
        if opt1.isEmpty || opt1.get.value != opt2.value
      } yield (name, opt2.value, if (opt1.isEmpty) "  (* unknown *)" else "")).toList

    val prefs =
      changed.sortBy(_._1)
        .map({ case (x, y, z) => x + " = " + Outer_Syntax.quote_string(y) + z + "\n" }).mkString

    Isabelle_System.mkdirs(file.dir)
    File.write_backup(file, "(* generated by Isabelle " + Date.now() + " *)\n\n" + prefs)
  }
}


class Options_Variable(init_options: Options)
{
  private var options = init_options

  def value: Options = synchronized { options }

  private def upd(f: Options => Options): Unit = synchronized { options = f(options) }
  def += (name: String, x: String): Unit = upd(opts => opts + (name, x))

  class Bool_Access
  {
    def apply(name: String): Boolean = value.bool(name)
    def update(name: String, x: Boolean): Unit = upd(opts => opts.bool.update(name, x))
  }
  val bool = new Bool_Access

  class Int_Access
  {
    def apply(name: String): Int = value.int(name)
    def update(name: String, x: Int): Unit = upd(opts => opts.int.update(name, x))
  }
  val int = new Int_Access

  class Real_Access
  {
    def apply(name: String): Double = value.real(name)
    def update(name: String, x: Double): Unit = upd(opts => opts.real.update(name, x))
  }
  val real = new Real_Access

  class String_Access
  {
    def apply(name: String): String = value.string(name)
    def update(name: String, x: String): Unit = upd(opts => opts.string.update(name, x))
  }
  val string = new String_Access

  def proper_string(name: String): Option[String] =
    Library.proper_string(string(name))

  def seconds(name: String): Time = value.seconds(name)
}
