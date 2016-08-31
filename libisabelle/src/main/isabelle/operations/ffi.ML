signature FFI = sig
  datatype ml_expr =
      Lit of string
    | App of ml_expr * ml_expr
    | Val of string * XML.tree

  val print_ml_expr: Proof.context -> ml_expr -> string
  val eval: Proof.context -> ml_expr -> string -> XML.tree

  val print_tree: XML.tree -> string
  val print_body: XML.body -> string

  (* internal *)
  val ml_expr_codec: ml_expr codec
  structure Eval : TYPED_EVAL
end

structure FFI : FFI = struct

structure Eval = Typed_Eval
(
  type T = XML.tree
  val typ = "XML.tree"
  val name = "FFI.Eval"
)

fun print_tree (XML.Elem elem) =
      let
        val str =
          ML_Syntax.print_pair
            (ML_Syntax.print_pair ML_Syntax.print_string ML_Syntax.print_properties)
            print_body
            elem
      in "(XML.Elem " ^ str  ^ ")" end
  | print_tree (XML.Text text) =
      "(XML.Text " ^ ML_Syntax.print_string text ^ ")"
and print_body body =
  ML_Syntax.print_list print_tree body

datatype ml_expr =
    Lit of string
  | App of ml_expr * ml_expr
  | Val of string * XML.tree

fun print_ml_expr _ (Lit text) =
      text
  | print_ml_expr ctxt (App (f, x)) =
      "(" ^ print_ml_expr ctxt f ^ ") (" ^ print_ml_expr ctxt x ^ ")"
  | print_ml_expr ctxt (Val (typ, value)) =
      let
        val codec = Classy.resolve @{ML.class codec} (ML_Types.read_ml_type typ) (Context.Proof ctxt)
      in "(Codec.the_decode " ^ codec ^ " " ^ print_tree value ^ ")" end

fun eval ctxt prog typ =
  let
    val codec = Classy.resolve @{ML.class codec} (ML_Types.read_ml_type typ) (Context.Proof ctxt)
    val prog = "(Codec.encode " ^ codec ^ " (" ^ print_ml_expr ctxt prog ^ "))"
  in
    Eval.eval (Input.string prog) ctxt
  end

fun ml_expr_codec () =
  let
    val ml_expr_lit = Codec.string
    fun ml_expr_app () = Codec.tuple (ml_expr_codec ()) (ml_expr_codec ())
    val ml_expr_val = Codec.tuple Codec.string Codec.tree

    fun enc _ = error "impossible"
    fun dec 0 = SOME (Codec.decode ml_expr_lit #> Codec.map_result Lit)
      | dec 1 = SOME (Codec.decode (ml_expr_app ()) #> Codec.map_result App)
      | dec 2 = SOME (Codec.decode ml_expr_val #> Codec.map_result Val)
      | dec _ = NONE
  in Codec.variant enc dec "FFI.ml_expr" end

val ml_expr_codec = ml_expr_codec ()

end