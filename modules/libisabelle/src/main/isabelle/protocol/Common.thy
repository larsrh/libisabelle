theory Common
imports Pure
begin

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
    | decode s = chr (Markup.parse_int (unprefix "#" s))

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
ML_file "protocol.ML"

end