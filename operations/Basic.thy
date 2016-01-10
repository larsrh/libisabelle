theory Basic
imports "../isabelle/$ISABELLE_VERSION/Protocol"
begin

operation_setup hello = \<open>
  {from_lib = Codec.string,
   to_lib = Codec.string,
   action = (fn data => "Hello " ^ data)}\<close>

operation_setup (sequential, bracket) use_thys = \<open>
  {from_lib = Codec.list Codec.string,
   to_lib = Codec.unit,
   action = Thy_Info.use_thys o map (rpair Position.none)}\<close>

text \<open>
  The read_term operation performs both parsing and checking at once, because we do not want to
  send back parsed, but un-checked terms to the JVM. They may contain weird position information
  which are difficult to get rid of and confuse the codecs.
\<close>

operation_setup read_term = \<open>
  {from_lib = Codec.triple Codec.string Codec.typ Codec.string,
   to_lib = Codec.option Codec.term,
   action = (fn (raw_term, typ, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      try (Syntax.parse_term ctxt) raw_term
      |> Option.map (Type.constraint typ)
      |> Option.mapPartial (try (Syntax.check_term ctxt))
    end)}\<close>

operation_setup check_term = \<open>
  {from_lib = Codec.triple Codec.term Codec.typ Codec.string,
   to_lib = Codec.option Codec.term,
   action = (fn (term, typ, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
      val term' = Type.constraint typ term
    in
      try (Syntax.check_term ctxt) term'
    end)}\<close>

end
