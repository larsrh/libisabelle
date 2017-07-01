package info.hupel.isabelle

import scala.concurrent._
import scala.math.BigInt

import monix.execution.CancelableFuture

import info.hupel.isabelle.ml.{Expr, Opaque}

sealed trait Instruction[A] {
  def run(sys: System, thyName: String)(implicit ec: ExecutionContext): CancelableFuture[ProverResult[A]]
}

object Instruction {

  private[isabelle] case class Ex[A](expr: Expr[A])(implicit A: Codec[A]) extends Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      expr.eval(sys, thyName)
  }

  private[isabelle] case class OpaqueEx[A, Repr](expr: Expr[A], conv: Expr[A => Repr])(implicit A: Opaque[A], Repr: Codec[Repr]) extends Instruction[(BigInt, Repr)] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext): CancelableFuture[ProverResult[(BigInt, Repr)]] =
      expr.opaqueEval(sys, thyName, conv)
  }

  private[isabelle] case class Op[I, O](operation: Operation[I, O], input: I) extends Instruction[O] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      sys.invoke(operation)(input)
  }

}
