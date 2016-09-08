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
    def get(name: String): ml.Expr[Theory] =
      ml.Expr.uncheckedLiteral[String => Theory]("Thy_Info.get_theory")(name)

    implicit val thyOpaque: ml.Opaque[Theory] = ml.Opaque.make("Refs.Thy")
  }
  type Theory = Theory.type

  object Context {
    def initGlobal(thy: ml.Expr[Theory]): ml.Expr[Context] =
      ml.Expr.uncheckedLiteral[Theory => Context]("Proof_Context.init_global")(thy)

    implicit val ctxtOpaque: ml.Opaque[Context] = ml.Opaque.make("Refs.Ctxt")
  }
  type Context = Context.type

  type Conv = Cterm => Thm

  object Cterm {
    def eval(ctxt: ml.Expr[Context], cterm: ml.Expr[Cterm]): ml.Expr[Thm] =
      ml.Expr.uncheckedLiteral[Context => Conv]("Code_Simp.dynamic_conv")(ctxt)(cterm)

    implicit val ctermOpaque: ml.Opaque[Cterm] = ml.Opaque.make("Refs.Cterm")
  }
  type Cterm = Cterm.type

  object Thm {
    def term(ct: ml.Expr[Cterm]): ml.Expr[Thm] =
      ml.Expr.uncheckedLiteral[Cterm => Thm]("Drule.mk_term")(ct)

    implicit val thmOpaque: ml.Opaque[Thm] = ml.Opaque.make("Refs.Thm")
  }
  type Thm = Thm.type

}
