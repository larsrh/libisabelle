package info.hupel.isabelle.ml

import scala.math.BigInt

final case class Ref[A : Opaque](id: BigInt) {
  val table: String = Opaque[A].table
  val read: Expr[A] =
    Expr.uncheckedLiteral[BigInt => A](s"$table.read")(id)
  val delete: Expr[Unit] =
    Expr.uncheckedLiteral[BigInt => Unit](s"$table.delete")(id)
}
