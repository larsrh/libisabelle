signature TYPED_EVAL = sig
  type T
  val eval: Input.source -> Proof.context -> T

  val fulfill: T -> Context.generic -> Context.generic (* internal *)
end

signature TYPED_EVAL_ARGS = sig
  type T
  val typ: string
  val name: string
end

functor Typed_Eval(Args : TYPED_EVAL_ARGS) : TYPED_EVAL = struct

structure Data = Generic_Data
(
  type T = Args.T option
  val empty = NONE
  val extend = I
  fun merge (NONE, NONE) = NONE
    | merge _ = error "Typed_Eval.Data.merge: can't merge"
)

type T = Args.T

val fulfill = Data.map o K o SOME

fun eval source ctxt =
  ML_Context.expression (Input.range_of source)
    "typed_eval_result" Args.typ
    (Args.name ^ ".fulfill typed_eval_result")
    (ML_Lex.read_source false source)
    (Context.Proof ctxt)
  |> Data.get
  |> the

end