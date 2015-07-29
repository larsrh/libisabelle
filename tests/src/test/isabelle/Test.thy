theory Test
imports Protocol
begin

operation_setup type_of = \<open>
  let
    val ctxt = Config.put show_markup false @{context}
    val read = Print_Mode.setmp []
      (Syntax.read_term @{context} #> fastype_of #> Syntax.string_of_typ ctxt)
  in
    {from_lib = Codec.string,
     to_lib = Codec.string,
     action = read}
  end
\<close>

end
