theory Protocol
imports "../isabelle-common/Codec"
keywords "operation_setup" :: thy_decl % "ML"
begin

ML\<open>
signature LIBISABELLE = sig
  type name = string
  type ('i, 'o) operation =
    {from_lib : 'i codec,
     to_lib : 'o codec,
     action : 'i -> 'o}

  type flags = {sequential: bool, bracket: bool}

  val default_flags : flags

  val add_operation : name -> ('i, 'o) operation -> flags -> unit
  val operation_setup : bstring -> Symbol_Pos.source -> flags -> Proof.context -> unit
end

structure Libisabelle : LIBISABELLE = struct

type name = string
type ('i, 'o) operation =
  {from_lib : 'i codec,
   to_lib : 'o codec,
   action : 'i -> 'o}

type flags = {sequential: bool, bracket: bool}

val default_flags = {sequential = false, bracket = false}

fun join_flags
  {sequential = seq1, bracket = br1}
  {sequential = seq2, bracket = br2} =
  {sequential = seq1 orelse seq2, bracket = br1 orelse br2}

type raw_operation = int -> XML.tree -> XML.tree

exception GENERIC of string

val operations =
  Synchronized.var "libisabelle.operations" (Symtab.empty: raw_operation Symtab.table)

val requests =
  Synchronized.var "libisabelle.requests" (Inttab.empty: (unit -> unit) Inttab.table)

fun sequentialize name f =
  let
    val var = Synchronized.var ("libisabelle." ^ name) ()
  in
    fn x => Synchronized.change_result var (fn _ => (f x, ()))
  end

fun bracketize f id x =
  let
    val start = [(Markup.functionN, "libisabelle_start"), ("id", Markup.print_int id)]
    val stop = [(Markup.functionN, "libisabelle_stop"), ("id", Markup.print_int id)]
    val _ = Output.protocol_message start []
    val res = f id x
    val _ = Output.protocol_message stop []
  in res end

fun add_operation name {from_lib, to_lib, action} {sequential, bracket} =
  let
    fun raw _ tree =
      case Codec.decode from_lib tree of
        Codec.Success i => Codec.encode to_lib (action i)
      | Codec.Failure (msg, _) => raise Fail ("decoding input failed for operation " ^ name ^ ": " ^ msg)
    val raw' = raw
      |> (if bracket then bracketize else I)
      |> (if sequential then sequentialize name else I)
  in
    Synchronized.change operations (Symtab.update (name, raw'))
  end

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn id :: name :: [arg] =>
    let
      val id = Markup.parse_int id
      val response = [(Markup.functionN, "libisabelle_response"), ("id", Markup.print_int id)]
      val args = YXML.parse arg
      fun exec f =
        let
          val future = Future.fork (fn () =>
            let
              val res = Exn.interruptible_capture (f id) args
              val yxml = YXML.string_of (Codec.encode (Codec.exn_result Codec.id) res)
            in
              Output.protocol_message response [yxml]
            end)
        in
          Synchronized.change requests (Inttab.update_new (id, fn () => Future.cancel future))
        end
    in
      (case Symtab.lookup (Synchronized.value operations) name of
        SOME operation => exec operation
      | NONE => exec (fn _ => raise Fail "libisabelle: unknown command"))
    end)

val _ = Isabelle_Process.protocol_command "libisabelle_cancel"
  (fn ids =>
    let
      fun remove id tab = (Inttab.lookup tab id, Inttab.delete_safe id tab)
      val _ =
        map Markup.parse_int ids
        |> fold_map remove
        |> Synchronized.change_result requests
        |> map (fn NONE => () | SOME f => f ())
    in
      ()
    end
  )

fun print_bool true = "true"
  | print_bool false = "false"

fun print_flags {sequential, bracket} =
  "({sequential=" ^ print_bool sequential ^ ",bracket=" ^ print_bool bracket ^ "})"

fun operation_setup name source flags ctxt =
  ML_Context.eval_in (SOME ctxt) ML_Compiler.flags (#pos source)
    (ML_Lex.read Position.none ("Libisabelle.add_operation " ^ ML_Syntax.print_string name ^ "(") @
      ML_Lex.read_source false source @
      ML_Lex.read Position.none ")" @
      ML_Lex.read Position.none (print_flags flags))

val _ =
  let
    val parse_flag =
      (Parse.reserved "sequential" || Parse.reserved "bracket") >>
        (fn flag => join_flags
           {sequential = flag = "sequential",
            bracket = flag = "bracket"})
    val parse_flags =
      Parse.list parse_flag >> (fn fs => fold (curry op o) fs I)
    val parse_cmd =
      Scan.optional (Args.parens parse_flags) I --
      Parse.name --
      Parse.!!! (@{keyword "="} |-- Parse.ML_source)
  in
    Outer_Syntax.command @{command_spec "operation_setup"} "define protocol operation in ML"
      (parse_cmd >> (fn ((flags, name), txt) =>
        Toplevel.keep (Toplevel.context_of #> operation_setup name txt (flags default_flags))))
  end

end
\<close>

operation_setup hello = \<open>
  {from_lib = Codec.string,
   to_lib = Codec.string,
   action = (fn data => "Hello " ^ data)}\<close>

operation_setup (sequential, bracket) use_thys = \<open>
  {from_lib = Codec.list Codec.string,
   to_lib = Codec.unit,
   action = Thy_Info.use_thys o map (rpair Position.none)}\<close>

end
