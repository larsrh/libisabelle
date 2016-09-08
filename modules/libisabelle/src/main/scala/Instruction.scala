package info.hupel.isabelle

import scala.concurrent._
import scala.math.BigInt

import cats.free.Free

import info.hupel.isabelle.ml.{Scope, Opaque}

sealed trait Instruction[A] {
  def run(sys: System, thyName: String)(implicit ec: ExecutionContext): Future[ProverResult[A]]
}

object Instruction {

  private[isabelle] case class Ex[A](expr: Scope#Expr[A])(implicit A: Codec[A]) extends Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      expr.eval(sys, thyName)
  }

  private[isabelle] case class OpaqueEx[A, Repr](expr: Scope#Expr[A], conv: ml.Expr[A => Repr])(implicit A: Opaque[A], Repr: Codec[Repr]) extends Instruction[(BigInt, Repr)] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext): Future[ProverResult[(BigInt, Repr)]] =
      expr.opaqueEval(sys, thyName, conv)
  }

  private[isabelle] case class Op[I, O](operation: Operation[I, O], input: I) extends Instruction[O] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      sys.invoke(operation)(input)
  }

}
