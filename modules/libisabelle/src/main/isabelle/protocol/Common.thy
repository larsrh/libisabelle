theory Common
imports Multi_Isabelle.Multi_Isabelle
begin

ML\<open>
  val print_int = Value.print_int
  val parse_int = Value.parse_int
  val print_bool = Value.print_bool
  val parse_bool = Value.parse_bool
  fun get_theory name =
    let
      val all = Thy_Info.get_names ()
      val qualified = find_first (fn name' => name = Long_Name.base_name name') all
    in Thy_Info.get_theory (the qualified) end
\<close>

ML_cond ("2017", "2018") \<open>
  val ml_lex_read_source = ML_Lex.read_source false
\<close>

ML_cond ("2019*") \<open>
  val ml_lex_read_source = ML_Lex.read_source
\<close>

ML\<open>
(* implementation from Isabelle2018 *)
fun ml_context_expression range name constraint body ants =
  ML_Context.exec (fn () =>
   ML_Context.eval ML_Compiler.flags (#1 range)
    (ML_Lex.read "Context.put_generic_context (SOME (let val " @ ML_Lex.read_set_range range name @
     ML_Lex.read (": " ^ constraint ^ " =") @ ants @
     ML_Lex.read ("in " ^ body ^ " end (Context.the_generic_context ())));")));
\<close>

ML\<open>
local
  (* code copied and adapted from Isabelle/Pure *)

  fun encode "<" = "&lt;"
    | encode ">" = "&gt;"
    | encode "&" = "&amp;"
    | encode "'" = "&apos;"
    | encode "\"" = "&quot;"
    | encode s =
        let val c = ord s in
          if c < 32 then "&#" ^ string_of_int c ^ ";"
          else if c < 127 then s
          else "&#" ^ string_of_int c ^ ";"
        end

  fun decode "lt" = "<"
    | decode "gt" = ">"
    | decode "amp" = "&"
    | decode "apos" = "'"
    | decode "quot" = "\""
    | decode s = chr (parse_int (unprefix "#" s))

  fun entity_char c = c <> ";"
  val parse_name = Scan.many entity_char

  val special = $$ "&" |-- (parse_name >> implode >> decode) --| $$ ";"
  val regular = Scan.one Symbol.not_eof
  val parse_chars = Scan.repeat (special || regular) >> implode
  val parse_string = Scan.read Symbol.stopper parse_chars o raw_explode
in
  val encode_string = translate_string encode
  val decode_string = the o parse_string
end
\<close>

ML_file "typed_eval.ML"
ML_file "codec.ML"
ML_file "ref_table.ML"
ML_file "protocol.ML"

syntax "_cartouche_xml" :: "cartouche_position \<Rightarrow> 'a"  ("XML _")

parse_translation\<open>
let
  fun translation args =
    let
      fun err () = raise TERM ("Common._cartouche_xml", args)
      fun input s pos = Symbol_Pos.implode (Symbol_Pos.cartouche_content (Symbol_Pos.explode (s, pos)))
      val eval = Codec.the_decode Codec.term o XML.parse
    in
      case args of
        [(c as Const (@{syntax_const "_constrain"}, _)) $ Free (s, _) $ p] =>
          (case Term_Position.decode_position p of
            SOME (pos, _) => c $ eval (input s pos) $ p
          | NONE => err ())
      | _ => err ()
  end
in
  [(@{syntax_const "_cartouche_xml"}, K translation)]
end
\<close>

end
