package info.hupel.isabelle.hol

import info.hupel.isabelle.pure._

object HOLogic {

  val boolT: Typ = Type("HOL.bool")
  val intT: Typ = Type("Int.int")

  val True: Term = Const("HOL.True", boolT)
  val False: Term = Const("HOL.False", boolT)

  val conj: Term = Const("HOL.conj", boolT -->: boolT -->: boolT)
  val disj: Term = Const("HOL.disj", boolT -->: boolT -->: boolT)
  val imp: Term = Const("HOL.implies", boolT -->: boolT -->: boolT)

  def equ(a: Typ): Term = Const("HOL.eq", a -->: a -->: boolT)

}
