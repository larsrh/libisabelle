/*  Title:      Pure/ML/ml_syntax.scala
    Author:     Makarius

Concrete ML syntax for basic values.
*/


// Ported from Isabelle2016-1

package isabelle


object ML_Syntax
{
  /* string */

  private def print_chr(c: Byte): String =
    c match {
      case 34 => "\\\""
      case 92 => "\\\\"
      case 9 => "\\t"
      case 10 => "\\n"
      case 12 => "\\f"
      case 13 => "\\r"
      case _ =>
        if (c < 0) "\\" + Library.signed_string_of_int(256 + c)
        else if (c < 32) new String(Array[Char](92, 94, (c + 64).toChar))
        else if (c < 127) Symbol.ascii(c.toChar)
        else "\\" + Library.signed_string_of_int(c)
    }

  def print_string0(str: String): String =
    quote(UTF8.bytes(str).iterator.map(print_chr(_)).mkString)
}
