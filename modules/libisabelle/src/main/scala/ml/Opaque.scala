package info.hupel.isabelle.ml

trait Opaque[A] {
  val table: String
}

object Opaque {
  def apply[A](implicit A: Opaque[A]): Opaque[A] = A

  def make[A](table0: String): Opaque[A] = new Opaque[A] {
    val table = table0
  }
}
