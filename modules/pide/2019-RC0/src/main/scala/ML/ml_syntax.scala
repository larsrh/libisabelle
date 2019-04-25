/*  Title:      Pure/ML/ml_syntax.scala
    Author:     Makarius

Concrete ML syntax for basic values.
*/

package isabelle


object ML_Syntax
{
  /* int */

  private def signed_int(s: String): String =
    if (s(0) == '-') "~" + s.substring(1) else s

  def print_int(i: Int): String = signed_int(Value.Int(i))
  def print_long(i: Long): String = signed_int(Value.Long(i))


  /* string */

  private def print_byte(c: Byte): String =
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

  private def print_symbol(s: Symbol.Symbol): String =
    if (s.startsWith("\\<")) s
    else UTF8.bytes(s).iterator.map(print_byte(_)).mkString

  def print_string_bytes(str: String): String =
    quote(UTF8.bytes(str).iterator.map(print_byte(_)).mkString)

  def print_string_symbols(str: String): String =
    quote(Symbol.iterator(str).map(print_symbol(_)).mkString)


  /* pair */

  def print_pair[A, B](f: A => String, g: B => String)(pair: (A, B)): String =
    "(" + f(pair._1) + ", " + g(pair._2) + ")"


  /* list */

  def print_list[A](f: A => String)(list: List[A]): String =
    "[" + commas(list.map(f)) + "]"


  /* properties */

  def print_properties(props: Properties.T): String =
    print_list(print_pair(print_string_bytes(_: String), print_string_bytes(_: String)))(props)
}
