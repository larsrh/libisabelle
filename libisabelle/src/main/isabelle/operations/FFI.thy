theory FFI
imports "../protocol/Protocol"
begin

ML_file "ffi.ML"

operation_setup eval_ml_expr = \<open>
  {from_lib = Codec.triple Codec.string FFI.ml_expr_codec Codec.string,
   to_lib = Codec.tree,
   action = fn (typ, prog, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      FFI.eval ctxt prog typ
    end}\<close>

operation_setup check_ml_expr = \<open>
  {from_lib = Codec.triple Codec.string FFI.ml_expr_codec Codec.string,
   to_lib = Codec.option Codec.string,
   action = fn (typ, prog, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      FFI.check ctxt prog typ
    end}\<close>

end