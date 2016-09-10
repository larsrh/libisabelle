package info.hupel.isabelle

package object ml extends Scope {

  implicit class GlobalScopeExprOps[A](e: Expr[A]) {

    def toProg(implicit A: Codec[A]): Program[A] =
      Program.expr(e)

    def rawPeek[Repr : Codec](conv: Expr[A => Repr])(implicit A: Opaque[A]): Program[(Ref[A], Repr)] =
      Program.rawPeek(e, conv)

    def peek[Repr : Codec, C](conv: Expr[A => Repr])(scoped: ml.Scoped[A, Repr, C])(implicit A: Opaque[A]): Program[C] =
      Program.peek(e, conv)(scoped)

  }

}
