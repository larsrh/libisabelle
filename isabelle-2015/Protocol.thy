theory Protocol
imports "../isabelle-common/Codec"
keywords "operation_setup" :: thy_decl % "ML"
begin

ML_file "../isabelle-common/protocol.ML"

ML\<open>
structure Protocol: PROTOCOL = struct

open Protocol

fun operation_setup_cmd name source flags ctxt =
  ML_Context.eval_in (SOME ctxt) ML_Compiler.flags (Input.pos_of source)
    (ML_Lex.read ("Protocol.add_operation " ^ ML_Syntax.print_string name ^ "(") @
      ML_Lex.read_source false source @
      ML_Lex.read ")" @
      ML_Lex.read (print_flags flags))

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
    Outer_Syntax.command @{command_keyword "operation_setup"} "define protocol operation in ML"
      (parse_cmd >> (fn ((flags, name), txt) =>
        Toplevel.keep (Toplevel.context_of #> operation_setup_cmd name txt (flags default_flags))))
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
