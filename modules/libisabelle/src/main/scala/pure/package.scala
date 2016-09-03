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
  }
  type Theory = Theory.type

  object Context {
    def initGlobal(thy: ml.Expr[Theory]): ml.Expr[Context] =
      ml.Expr.uncheckedLiteral[Theory => Context]("Proof_Context.init_global")(thy)
  }
  type Context = Context.type

  type Conv = CTerm => Thm

  object CTerm {
    def eval(ctxt: ml.Expr[Context], cterm: ml.Expr[CTerm]): ml.Expr[Thm] =
      ml.Expr.uncheckedLiteral[Context => Conv]("Code_Simp.dynamic_conv")(ctxt)(cterm)
  }
  type CTerm = CTerm.type

  object Thm {
    def term(ct: ml.Expr[CTerm]): ml.Expr[Thm] =
      ml.Expr.uncheckedLiteral[CTerm => Thm]("Drule.mk_term")(ct)
  }
  type Thm = Thm.type

}
