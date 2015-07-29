package edu.tum.cs.isabelle

import scala.concurrent.ExecutionContext

import edu.tum.cs.isabelle.api.Environment

case class DecodingException(msg: String, body: Environment#XMLBody) extends RuntimeException(msg)

object `package` {

  private[isabelle] implicit class ListOps[A](as: List[A]) {
    def traverse[E, B](f: A => Either[E, B]): Either[E, List[B]] = {
      @annotation.tailrec
      def go(as: List[A], bs: List[B]): Either[E, List[B]] = as match {
        case Nil => Right(bs)
        case a :: as =>
          f(a) match {
            case Right(b) => go(as, b :: bs)
            case Left(err) => Left(err)
          }
      }

      go(as, Nil).right.map(_.reverse)
    }
  }

  type XMLResult[+A] = Either[(String, Environment#XMLBody), A]

}
