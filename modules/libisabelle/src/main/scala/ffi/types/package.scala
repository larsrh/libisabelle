package info.hupel.isabelle.ffi

package object types {

  object Theory {
    def get(name: String): MLExpr[Theory] =
      MLExpr.uncheckedLiteral[String => Theory]("Thy_Info.get_theory")(name)
  }
  type Theory = Theory.type

  object Context {
    def initGlobal(thy: MLExpr[Theory]): MLExpr[Context] =
      MLExpr.uncheckedLiteral[Theory => Context]("Proof_Context.init_global")(thy)
  }
  type Context = Context.type

  type Conv = CTerm => Thm

  object CTerm {
    def eval(ctxt: MLExpr[Context], cterm: MLExpr[CTerm]): MLExpr[Thm] =
      MLExpr.uncheckedLiteral[Context => Conv]("Code_Simp.dynamic_conv")(ctxt)(cterm)
  }
  type CTerm = CTerm.type

  object Thm {
    def term(ct: MLExpr[CTerm]): MLExpr[Thm] =
      MLExpr.uncheckedLiteral[CTerm => Thm]("Drule.mk_term")(ct)
  }
  type Thm = Thm.type

}
