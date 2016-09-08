package info.hupel.isabelle

import scala.language.experimental.macros

/**
 * Collection of standard types for communication with Isabelle.
 */
package object pure {

  type Indexname = (String, BigInt)
  type Sort = List[String]

  implicit class ExprStringContext(ctx: StringContext) {
    object term {
      def apply[T](args: T*): Program[String] = macro internal.Macros.term
    }
  }

  object Theory {
    val get: ml.Expr[String => Theory] =
      ml.Expr.uncheckedLiteral("Thy_Info.get_theory")

    implicit val thyOpaque: ml.Opaque[Theory] = ml.Opaque.make("Refs.Thy")
  }
  type Theory = Theory.type

  object Context {
    val initGlobal: ml.Expr[Theory => Context] =
      ml.Expr.uncheckedLiteral("Proof_Context.init_global")

    implicit val ctxtOpaque: ml.Opaque[Context] = ml.Opaque.make("Refs.Ctxt")
  }
  type Context = Context.type

  type Conv = Cterm => Thm

  object Cterm {
    val eval: ml.Expr[Context => Conv] =
      ml.Expr.uncheckedLiteral("Code_Simp.dynamic_conv")

    implicit val ctermOpaque: ml.Opaque[Cterm] = ml.Opaque.make("Refs.Cterm")
  }
  type Cterm = Cterm.type

  object Thm {
    val mkTerm: ml.Expr[Cterm => Thm] =
      ml.Expr.uncheckedLiteral("Drule.mk_term")

    implicit val thmOpaque: ml.Opaque[Thm] = ml.Opaque.make("Refs.Thm")
  }
  type Thm = Thm.type

}
