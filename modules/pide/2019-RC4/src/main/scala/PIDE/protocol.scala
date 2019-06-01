/*  Title:      Pure/PIDE/protocol.scala
    Author:     Makarius

Protocol message formats for interactive proof documents.
*/

package isabelle


object Protocol
{
  /* document editing */

  object Assign_Update
  {
    def unapply(text: String)
      : Option[(Document_ID.Version, List[String], Document.Assign_Update)] =
    {
      try {
        import XML.Decode._
        def decode_upd(body: XML.Body): (Long, List[Long]) =
          space_explode(',', string(body)).map(Value.Long.parse) match {
            case a :: bs => (a, bs)
            case _ => throw new XML.XML_Body(body)
          }
        Some(triple(long, list(string), list(decode_upd _))(Symbol.decode_yxml(text)))
      }
      catch {
        case ERROR(_) => None
        case _: XML.Error => None
      }
    }
  }

  object Removed
  {
    def unapply(text: String): Option[List[Document_ID.Version]] =
      try {
        import XML.Decode._
        Some(list(long)(Symbol.decode_yxml(text)))
      }
      catch {
        case ERROR(_) => None
        case _: XML.Error => None
      }
  }


  /* command timing */

  object Command_Timing
  {
    def unapply(props: Properties.T): Option[(Document_ID.Generic, isabelle.Timing)] =
      props match {
        case Markup.COMMAND_TIMING :: args =>
          (args, args) match {
            case (Position.Id(id), Markup.Timing_Properties(timing)) => Some((id, timing))
            case _ => None
          }
        case _ => None
      }
  }


  /* theory timing */

  object Theory_Timing
  {
    def unapply(props: Properties.T): Option[(String, isabelle.Timing)] =
      props match {
        case Markup.THEORY_TIMING :: args =>
          (args, args) match {
            case (Markup.Name(name), Markup.Timing_Properties(timing)) => Some((name, timing))
            case _ => None
          }
        case _ => None
      }
  }


  /* result messages */

  def is_result(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.RESULT, _), _) => true
      case _ => false
    }

  def is_tracing(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.TRACING, _), _) => true
      case XML.Elem(Markup(Markup.TRACING_MESSAGE, _), _) => true
      case _ => false
    }

  def is_state(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.STATE, _), _) => true
      case XML.Elem(Markup(Markup.STATE_MESSAGE, _), _) => true
      case _ => false
    }

  def is_information(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.INFORMATION, _), _) => true
      case XML.Elem(Markup(Markup.INFORMATION_MESSAGE, _), _) => true
      case _ => false
    }

  def is_writeln(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.WRITELN, _), _) => true
      case XML.Elem(Markup(Markup.WRITELN_MESSAGE, _), _) => true
      case _ => false
    }

  def is_warning(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.WARNING, _), _) => true
      case XML.Elem(Markup(Markup.WARNING_MESSAGE, _), _) => true
      case _ => false
    }

  def is_legacy(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.LEGACY, _), _) => true
      case XML.Elem(Markup(Markup.LEGACY_MESSAGE, _), _) => true
      case _ => false
    }

  def is_error(msg: XML.Tree): Boolean =
    msg match {
      case XML.Elem(Markup(Markup.ERROR, _), _) => true
      case XML.Elem(Markup(Markup.ERROR_MESSAGE, _), _) => true
      case _ => false
    }

  def is_inlined(msg: XML.Tree): Boolean =
    !(is_result(msg) || is_tracing(msg) || is_state(msg))

  def is_exported(msg: XML.Tree): Boolean =
    is_writeln(msg) || is_warning(msg) || is_legacy(msg) || is_error(msg)


  /* breakpoints */

  object ML_Breakpoint
  {
    def unapply(tree: XML.Tree): Option[Long] =
    tree match {
      case XML.Elem(Markup(Markup.ML_BREAKPOINT, Markup.Serial(breakpoint)), _) => Some(breakpoint)
      case _ => None
    }
  }


  /* dialogs */

  object Dialog_Args
  {
    def unapply(props: Properties.T): Option[(Document_ID.Generic, Long, String)] =
      (props, props, props) match {
        case (Position.Id(id), Markup.Serial(serial), Markup.Result(result)) =>
          Some((id, serial, result))
        case _ => None
      }
  }

  object Dialog
  {
    def unapply(tree: XML.Tree): Option[(Document_ID.Generic, Long, String)] =
      tree match {
        case XML.Elem(Markup(Markup.DIALOG, Dialog_Args(id, serial, result)), _) =>
          Some((id, serial, result))
        case _ => None
      }
  }

  object Dialog_Result
  {
    def apply(id: Document_ID.Generic, serial: Long, result: String): XML.Elem =
    {
      val props = Position.Id(id) ::: Markup.Serial(serial)
      XML.Elem(Markup(Markup.RESULT, props), List(XML.Text(result)))
    }

    def unapply(tree: XML.Tree): Option[String] =
      tree match {
        case XML.Elem(Markup(Markup.RESULT, _), List(XML.Text(result))) => Some(result)
        case _ => None
      }
  }
}


trait Protocol
{
  /* protocol commands */

  def protocol_command_bytes(name: String, args: Bytes*): Unit
  def protocol_command(name: String, args: String*): Unit


  /* options */

  def options(opts: Options): Unit =
    protocol_command("Prover.options", Symbol.encode_yxml(opts.encode))


  /* session base */

  private def encode_table(table: List[(String, String)]): String =
  {
    import XML.Encode._
    Symbol.encode_yxml(list(pair(string, string))(table))
  }

  private def encode_list(lst: List[String]): String =
  {
    import XML.Encode._
    Symbol.encode_yxml(list(string)(lst))
  }

  private def encode_sessions(lst: List[(String, Position.T)]): String =
  {
    import XML.Encode._
    Symbol.encode_yxml(list(pair(string, properties))(lst))
  }

  def session_base(resources: Resources)
  {
    val base = resources.session_base.standard_path
    protocol_command("Prover.init_session_base",
      encode_sessions(base.known.sessions.toList),
      encode_list(base.doc_names),
      encode_table(base.global_theories.toList),
      encode_list(base.loaded_theories.keys),
      encode_table(base.dest_known_theories))
  }


  /* interned items */

  def define_blob(digest: SHA1.Digest, bytes: Bytes): Unit =
    protocol_command_bytes("Document.define_blob", Bytes(digest.toString), bytes)

  def define_command(command: Command)
  {
    val blobs_yxml =
    {
      import XML.Encode._
      val encode_blob: T[Command.Blob] =
        variant(List(
          { case Exn.Res((a, b)) =>
              (Nil, pair(string, option(string))((a.node, b.map(p => p._1.toString)))) },
          { case Exn.Exn(e) => (Nil, string(Exn.message(e))) }))

      Symbol.encode_yxml(pair(list(encode_blob), int)(command.blobs, command.blobs_index))
    }

    val toks = command.span.content
    val toks_yxml =
    {
      import XML.Encode._
      val encode_tok: T[Token] = (tok => pair(int, int)((tok.kind.id, Symbol.length(tok.source))))
      Symbol.encode_yxml(list(encode_tok)(toks))
    }

    protocol_command("Document.define_command",
      (Document_ID(command.id) :: Symbol.encode(command.span.name) :: blobs_yxml :: toks_yxml ::
        toks.map(tok => Symbol.encode(tok.source))): _*)
  }


  /* execution */

  def discontinue_execution(): Unit =
    protocol_command("Document.discontinue_execution")

  def cancel_exec(id: Document_ID.Exec): Unit =
    protocol_command("Document.cancel_exec", Document_ID(id))


  /* document versions */

  def update(old_id: Document_ID.Version, new_id: Document_ID.Version,
    edits: List[Document.Edit_Command], consolidate: List[Document.Node.Name])
  {
    val consolidate_yxml =
    {
      import XML.Encode._
      Symbol.encode_yxml(list(string)(consolidate.map(_.node)))
    }
    val edits_yxml =
    {
      import XML.Encode._
      def id: T[Command] = (cmd => long(cmd.id))
      def encode_edit(name: Document.Node.Name)
          : T[Document.Node.Edit[Command.Edit, Command.Perspective]] =
        variant(List(
          { case Document.Node.Edits(a) => (Nil, list(pair(option(id), option(id)))(a)) },
          { case Document.Node.Deps(header) =>
              val master_dir = File.standard_url(name.master_dir)
              val imports = header.imports.map({ case (a, _) => a.node })
              val keywords =
                header.keywords.map({ case (a, Keyword.Spec(b, c, d)) => (a, ((b, c), d)) })
              (Nil,
                pair(string, pair(string, pair(list(string), pair(list(pair(string,
                    pair(pair(string, list(string)), list(string)))), list(string)))))(
                (master_dir, (name.theory, (imports, (keywords, header.errors)))))) },
          { case Document.Node.Perspective(a, b, c) =>
              (bool_atom(a) :: b.commands.map(cmd => long_atom(cmd.id)),
                list(pair(id, pair(string, list(string))))(c.dest)) }))
      edits.map({ case (name, edit) =>
        Symbol.encode_yxml(pair(string, encode_edit(name))(name.node, edit)) })
    }
    protocol_command("Document.update",
      (Document_ID(old_id) :: Document_ID(new_id) :: consolidate_yxml :: edits_yxml): _*)
  }

  def remove_versions(versions: List[Document.Version])
  {
    val versions_yxml =
    { import XML.Encode._
      Symbol.encode_yxml(list(long)(versions.map(_.id))) }
    protocol_command("Document.remove_versions", versions_yxml)
  }


  /* dialog via document content */

  def dialog_result(serial: Long, result: String): Unit =
    protocol_command("Document.dialog_result", Value.Long(serial), result)
}
