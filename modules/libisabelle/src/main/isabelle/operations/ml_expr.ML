signature ML_EXPR = sig
  datatype ml_expr =
      Lit of string
    | App of ml_expr * ml_expr
    | Val of string * XML.tree

  val print_ml_expr: Proof.context -> ml_expr -> string
  val eval: Proof.context -> ml_expr -> string -> XML.tree
  val check: Proof.context -> ml_expr -> string -> string option

  val print_tree: XML.tree -> string
  val print_body: XML.body -> string

  (* internal *)
  val codec: ml_expr codec
  structure Eval : TYPED_EVAL
end

structure ML_Expr : ML_EXPR = struct

structure Eval = Typed_Eval
(
  type T = XML.tree
  val typ = "XML.tree"
  val name = "ML_Expr.Eval"
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

fun check ctxt prog raw_typ =
  case try ML_Types.read_ml_type raw_typ of
    NONE => SOME ("failed to parse result type " ^ raw_typ)
  | SOME typ =>
      let
        val context = Context.Proof ctxt
        val codec = can (Classy.resolve @{ML.class codec} typ) context

        fun check_vals (Lit _) = NONE
          | check_vals (App (f, x)) = merge_options (check_vals f, check_vals x)
          | check_vals (Val (raw_typ, _)) =
              case try ML_Types.read_ml_type raw_typ of
                NONE => SOME ("failed to parse value type " ^ raw_typ)
              | SOME _ =>
                  if can (Classy.resolve @{ML.class codec} typ) context then
                    NONE
                  else
                    SOME ("could not resolve codec for value type " ^ raw_typ)
      in
        if not codec then
          SOME ("could not resolve codec for result type " ^ raw_typ)
        else
          case check_vals prog of
            SOME err => SOME err
          | NONE =>
              case Exn.capture (ML_Types.ml_type_of ctxt) (print_ml_expr ctxt prog) of
                Exn.Res typ' =>
                  if typ = typ' then
                    NONE
                  else
                    SOME ("expected result type " ^ raw_typ ^ " but got something else")
              | Exn.Exn exn =>
                  SOME ("compilation error: " ^ @{make_string} exn)
      end

fun eval ctxt prog typ =
  let
    val codec = Classy.resolve @{ML.class codec} (ML_Types.read_ml_type typ) (Context.Proof ctxt)
    val prog = "(Codec.encode " ^ codec ^ " (" ^ print_ml_expr ctxt prog ^ "))"
  in
    Eval.eval (Input.string prog) ctxt
  end

fun codec () =
  let
    val ml_expr_lit = Codec.string
    fun ml_expr_app () = Codec.tuple (codec ()) (codec ())
    val ml_expr_val = Codec.tuple Codec.string Codec.tree

    fun enc _ = error "impossible"
    fun dec 0 = SOME (Codec.decode ml_expr_lit #> Codec.map_result Lit)
      | dec 1 = SOME (Codec.decode (ml_expr_app ()) #> Codec.map_result App)
      | dec 2 = SOME (Codec.decode ml_expr_val #> Codec.map_result Val)
      | dec _ = NONE
  in Codec.variant enc dec "ML_Expr.ml_expr" end

val codec = codec ()

end