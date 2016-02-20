theory HOL_Operations
imports "../protocol/Protocol" Main
begin

operation_setup mk_int = \<open>
  {from_lib = Codec.int,
   to_lib = Codec.term,
   action = HOLogic.mk_number @{typ int}}\<close>

operation_setup mk_list = \<open>
  {from_lib = Codec.tuple Codec.typ (Codec.list Codec.term),
   to_lib = Codec.term,
   action = uncurry HOLogic.mk_list}
\<close>

end
