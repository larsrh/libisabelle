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

ML\<open>
(* FIXME is this somewhere in the library? Term_Position.strip_positions doesn't do it *)
fun strip_positions ((t as Const ("_type_constraint_", Type ("fun", [t1, _]))) $ u) =
      if is_some (Term_Position.decode_positionT t1) then
        strip_positions u
      else
        t $ strip_positions u
  | strip_positions (t $ u) = strip_positions t $ strip_positions u
  | strip_positions (Abs (x, T, t)) = Abs (x, T, strip_positions t)
  | strip_positions t = t
\<close>

operation_setup parse_term = \<open>
  {from_lib = Codec.tuple Codec.string Codec.string,
   to_lib = Codec.option Codec.term,
   action = (fn (raw_term, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      try (Syntax.parse_term ctxt) raw_term
      |> Option.map strip_positions
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