theory ML_Expr
imports "../protocol/Protocol"
begin

ML_file "ml_expr.ML"

operation_setup eval_expr = \<open>
  {from_lib = Codec.triple Codec.string ML_Expr.codec Codec.string,
   to_lib = Codec.tree,
   action = fn (typ, prog, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      ML_Expr.eval ctxt prog typ
    end}\<close>

operation_setup check_expr = \<open>
  {from_lib = Codec.triple Codec.string ML_Expr.codec Codec.string,
   to_lib = Codec.option Codec.string,
   action = fn (typ, prog, thy_name) =>
    let
      val thy = Thy_Info.get_theory thy_name
      val ctxt = Proof_Context.init_global thy
    in
      ML_Expr.check ctxt prog typ
    end}\<close>

end
