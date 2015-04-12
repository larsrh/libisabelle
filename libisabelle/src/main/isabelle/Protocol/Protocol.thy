theory Protocol
imports Codec
keywords "operation_setup" :: thy_decl % "ML"
begin

ML\<open>
signature LIBISABELLE = sig
  type name = string
  type ('i, 'o) operation =
    {from_lib : 'i codec,
     to_lib : 'o codec,
     action : 'i -> 'o}

  val add_operation : name -> ('i, 'o) operation -> unit
  val operation_setup : bstring -> Symbol_Pos.source -> theory -> unit
end

structure Libisabelle : LIBISABELLE = struct

type name = string
type ('i, 'o) operation =
  {from_lib : 'i codec,
   to_lib : 'o codec,
   action : 'i -> 'o}

type raw_operation = XML.tree -> XML.tree

exception GENERIC of string

val operations =
  Synchronized.var "libisabelle.operations" (Symtab.empty: raw_operation Symtab.table)

fun add_operation name {from_lib, to_lib, action} =
  let
    fun raw tree =
      case Codec.decode from_lib tree of
        Codec.Success i => Codec.encode to_lib (action i)
      | Codec.Failure (msg, _) => raise Fail ("decoding input failed " ^ msg)
  in
    Synchronized.change operations (Symtab.update (name, raw))
  end

val _ = Isabelle_Process.protocol_command "libisabelle"
  (fn id :: name :: [arg] =>
    let
      val id = Markup.parse_int id
      val response =
        [(Markup.functionN, "libisabelle_response"),
         ("id", Markup.print_int id)]
      val args = YXML.parse arg
      fun exec f =
        (Future.fork (fn () =>
          let
            val res = Exn.interruptible_capture f args
            val yxml = YXML.string_of (Codec.encode (Codec.exn_result Codec.id) res)
          in
            Output.protocol_message response [yxml]
          end);
        ())
    in
      (case Symtab.lookup (Synchronized.value operations) name of
        SOME operation => exec operation
      | NONE => exec (fn _ => raise Fail "libisabelle: unknown command"))
    end)

fun operation_setup name source thy =
  ML_Context.eval_in (SOME (Proof_Context.init_global thy)) ML_Compiler.flags (#pos source)
    (ML_Lex.read Position.none ("Libisabelle.add_operation " ^ ML_Syntax.print_string name ^ "(") @
      ML_Lex.read_source false source @
      ML_Lex.read Position.none ")")

end

val _ = Outer_Syntax.command @{command_spec "operation_setup"} "define protocol operation in ML"
  (Parse.name -- Parse.!!! (@{keyword "="} |-- Parse.ML_source)
    >> (fn (name, txt) => Toplevel.theory (tap (Libisabelle.operation_setup name txt))))
\<close>

operation_setup hello = \<open>
  {from_lib = Codec.string,
   to_lib = Codec.string,
   action = (fn data => "Hello " ^ data)}\<close>

operation_setup use_thys = \<open>
  {from_lib = Codec.list Codec.string,
   to_lib = Codec.unit,
   action = Thy_Info.use_thys o map (rpair Position.none)}\<close>

end
