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

  object CTerm
  type CTerm = CTerm.type

}
