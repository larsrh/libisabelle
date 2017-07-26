package info.hupel.isabelle.api

import java.lang.{String => JString}

sealed abstract class OptionKey[T] {
  def set(value: T): OptionKey.Update
}

object OptionKey {

  case class Integer(key: JString) extends OptionKey[Int] {
    def set(value: Int) = Update(key, value.toString)
  }
  case class String(key: JString) extends OptionKey[JString] {
    def set(value: JString) = Update(key, value)
  }
  case class Real(key: JString) extends OptionKey[Double] {
    def set(value: Double) = Update(key, value.toString)
  }
  case class Bool(key: JString) extends OptionKey[Boolean] {
    def set(value: Boolean) = Update(key, if (value) "true" else "false")
  }

  case class Update private[OptionKey](key: JString, value: JString)

}
