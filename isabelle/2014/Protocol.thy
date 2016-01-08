theory Protocol
imports "../../common/Common"
keywords "operation_setup" :: thy_decl % "ML"
begin

ML\<open>
val _ =
  let
    open Protocol

    fun operation_setup_cmd name source flags ctxt =
      ML_Context.eval_in (SOME ctxt) ML_Compiler.flags (#pos source)
        (ML_Lex.read Position.none ("Protocol.add_operation " ^ ML_Syntax.print_string name ^ "(") @
          ML_Lex.read_source false source @
          ML_Lex.read Position.none ")" @
          ML_Lex.read Position.none (print_flags flags))

    val parse_flag =
      (Parse.reserved "sequential" || Parse.reserved "bracket") >>
        (fn flag => join_flags
           {sequential = flag = "sequential",
            bracket = flag = "bracket",
            auto = false (* not supported by Isabelle2014 *)})
    val parse_flags =
      Parse.list parse_flag >> (fn fs => fold (curry op o) fs I)
    val parse_cmd =
      Scan.optional (Args.parens parse_flags) I --
      Parse.name --
      Parse.!!! (@{keyword "="} |-- Parse.ML_source)
  in
    Outer_Syntax.command @{command_spec "operation_setup"} "define protocol operation in ML"
      (parse_cmd >> (fn ((flags, name), txt) =>
        Toplevel.keep (Toplevel.context_of #> operation_setup_cmd name txt (flags default_flags))))
  end
\<close>

end
