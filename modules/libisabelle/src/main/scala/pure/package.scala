package info.hupel.isabelle

import scala.language.experimental.macros

/**
 * Collection of standard types for communication with Isabelle.
 */
package object pure {

  type Indexname = (String, BigInt)
  type Sort = List[String]

  implicit class ExprStringContext(ctx: StringContext) {
    object term {
      def apply[T](args: T*): Program[String] = macro internal.Macros.term
    }
  }

}
