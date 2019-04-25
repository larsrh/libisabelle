/*  Title:      Pure/Tools/print_operation.scala
    Author:     Makarius

Print operations as asynchronous query.
*/

package isabelle


object Print_Operation
{
  def print_operations(session: Session): List[(String, String)] =
    session.get_protocol_handler("isabelle.Print_Operation$Handler") match {
      case Some(handler: Handler) => handler.get
      case _ => Nil
    }


  /* protocol handler */

  class Handler extends Session.Protocol_Handler
  {
    private val print_operations = Synchronized[List[(String, String)]](Nil)

    override def init(session: Session): Unit =
      session.protocol_command(Markup.PRINT_OPERATIONS)

    def get: List[(String, String)] = print_operations.value

    private def put(msg: Prover.Protocol_Output): Boolean =
    {
      val ops =
      {
        import XML.Decode._
        list(pair(string, string))(Symbol.decode_yxml(msg.text))
      }
      print_operations.change(_ => ops)
      true
    }

    val functions = List(Markup.PRINT_OPERATIONS -> put _)
  }
}
