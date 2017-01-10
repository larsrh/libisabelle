package info.hupel

import scala.concurrent._
import scala.util._

import monix.execution.CancelableFuture
import monix.execution.cancelables.MultiAssignmentCancelable

import cats.free.Free

import scalatags.Text

import info.hupel.isabelle.api.XML
import info.hupel.isabelle.ml.Ref

package object isabelle {

  /**
   * The result type for [[Codec#decode decoding values]] from
   * [[info.hupel.isabelle.api.XML.Tree XML trees]]. Failure values
   * should contain an error message and a list of erroneous trees.
   */
  type XMLResult[+A] = Either[(String, XML.Body), A]

  type HTML = Text.TypedTag[String]

  type Program[A] = Free[Instruction, A]

  object Program {

    def pure[A](a: A): Program[A] =
      Free.pure(a)

    def expr[A : Codec](mlExpr: ml.Expr[A]): Program[A] =
      Free.liftF[Instruction, A](Instruction.Ex(mlExpr))

    def rawPeek[A : ml.Opaque, Repr : Codec](mlExpr: ml.Expr[A], conv: ml.Expr[A => Repr]): Program[(Ref[A], Repr)] =
      mlExpr.rawPeek(conv)

    def peek[A : ml.Opaque, Repr : Codec, C](mlExpr: ml.Expr[A], conv: ml.Expr[A => Repr])(f: (Repr, ml.Expr[A]) => Program[C]): Program[C] =
      mlExpr.peek(conv)(f)

    def operation[I, O](operation: Operation[I, O], input: I): Program[O] =
      Free.liftF[Instruction, O](Instruction.Op(operation, input))

  }

  final implicit class CancelableFutureOps[A](future: CancelableFuture[A]) {
    def flatMapC[B](cont: A => CancelableFuture[B])(implicit ec: ExecutionContext): CancelableFuture[B] = {
      val conn = MultiAssignmentCancelable()
      val c1 = future.flatMap { a =>
        val c2 = cont(a)
        conn.orderedUpdate(c2, order = 2)
        c2
      }

      conn.orderedUpdate(c1, order = 1)
      CancelableFuture(c1, conn)
    }
  }

}
