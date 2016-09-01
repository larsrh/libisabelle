package info.hupel.isabelle.hol

import info.hupel.isabelle.pure._

object HOLogic {

  val boolT = Type("HOL.bool")
  val intT = Type("Int.int")

  val True = Const("HOL.True", boolT)
  val False = Const("HOL.False", boolT)

  val conj = Const("HOL.conj", boolT -->: boolT -->: boolT)
  val disj = Const("HOL.disj", boolT -->: boolT -->: boolT)
  val imp = Const("HOL.implies", boolT -->: boolT -->: boolT)

  def equ(a: Typ) = Const("HOL.eq", a -->: a -->: boolT)

}
