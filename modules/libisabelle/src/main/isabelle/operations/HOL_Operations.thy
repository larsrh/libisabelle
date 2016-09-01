theory HOL_Operations
imports "../protocol/Protocol" Main
begin

operation_setup mk_int = \<open>
  {from_lib = Codec.int,
   to_lib = Codec.term,
   action = HOLogic.mk_number @{typ int}}\<close>

operation_setup dest_int = \<open>
  {from_lib = Codec.term,
   to_lib = Codec.option Codec.int,
   action = fn t => case try HOLogic.dest_number t of
      NONE => NONE
    | SOME (typ, n) =>
        if typ = @{typ int} then
          SOME n
        else
          NONE}\<close>

operation_setup (auto) mk_list = \<open>uncurry HOLogic.mk_list\<close>
operation_setup (auto) dest_list = \<open>try HOLogic.dest_list\<close>

end
