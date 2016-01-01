theory Protocol
imports Codec_Class
keywords "operation_setup" :: thy_decl % "ML"
begin

ML_file "../isabelle-common/protocol.ML"

ML\<open>
val _ =
  let
    open Protocol
    fun operation_setup_cmd name source (flags as {auto, ...}) ctxt =
      let
        fun eval enclose =
          ML_Context.eval_in (SOME ctxt) ML_Compiler.flags (Input.pos_of source)
            (ML_Lex.read ("Protocol.add_operation " ^ ML_Syntax.print_string name ^ "(") @
              enclose (ML_Lex.read_source false source) @
              ML_Lex.read ")" @
              ML_Lex.read (print_flags flags))
      in
        if auto then
          let
            val ML_Types.Fun (arg, res) = ML_Types.ml_type_of ctxt (Input.source_content source)
            val arg_codec = Classy.resolve @{ML.class codec} arg (Context.Proof ctxt)
            val res_codec = Classy.resolve @{ML.class codec} res (Context.Proof ctxt)
            fun enclose toks =
              ML_Lex.read "{from_lib=" @ ML_Lex.read arg_codec @ ML_Lex.read "," @
              ML_Lex.read "to_lib=" @ ML_Lex.read res_codec @ ML_Lex.read "," @
              ML_Lex.read "action=" @ toks @ ML_Lex.read "}"
          in
            eval enclose
          end
        else
          eval I
      end
    val parse_flag =
      (Parse.reserved "sequential" || Parse.reserved "bracket" || Parse.reserved "auto") >>
        (fn flag => join_flags
           {sequential = flag = "sequential",
            bracket = flag = "bracket",
            auto = flag = "auto"})
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
\<close>

operation_setup (auto) hello =
  \<open>fn data => "Hello " ^ data\<close>

operation_setup (sequential, bracket, auto) use_thys =
  \<open>Thy_Info.use_thys o map (rpair Position.none)\<close>

end
