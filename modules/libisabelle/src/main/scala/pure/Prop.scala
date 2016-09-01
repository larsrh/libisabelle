package info.hupel.isabelle.pure

sealed abstract class Prop

object Prop {
  implicit def propTypeable: Typeable[Prop] =
    Typeable.make(Type("prop", Nil))
}
