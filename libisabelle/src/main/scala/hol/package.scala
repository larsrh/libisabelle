package edu.tum.cs.isabelle

import scala.concurrent.Future
import scala.math.BigInt

import edu.tum.cs.isabelle.pure._

package object hol {

  implicit def bigIntTypeable: Typeable[BigInt] = Typeable.make(Type("Int.int", Nil))
  implicit def boolTypeable: Typeable[Boolean] = Typeable.make(Type("HOL.bool", Nil))

  implicit def listTypeable[T : Typeable]: Typeable[List[T]] = Typeable.make(Type("List.list", List(Typeable.typ[T])))

}
