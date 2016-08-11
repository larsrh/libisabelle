package info.hupel.isabelle.pure

class Prop private(term: Term)

object Prop {
  implicit def propTypeable: Typeable[Prop] =
    Typeable.make(Type("prop", Nil))
}
