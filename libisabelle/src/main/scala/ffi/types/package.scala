package info.hupel.isabelle.ffi

import shapeless.Witness

package object types {

  val Theory = Witness("theory")
  type Theory = Theory.T

  val Context = Witness("context")
  type Context = Context.T

  val CTerm = Witness("cterm")
  type CTerm = CTerm.T

}
