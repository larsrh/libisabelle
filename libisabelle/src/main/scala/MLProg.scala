package info.hupel.isabelle

import scala.concurrent._

import cats.free.Free

import info.hupel.isabelle.ffi.MLExpr

object MLProg {

  sealed trait Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext): Future[ProverResult[A]]
  }

  private case class Ex[A](expr: MLExpr[A])(implicit A: Codec[A]) extends Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      expr.eval(sys, expr, thyName)
  }

  private case class Op[I, O](operation: Operation[I, O], input: I) extends Instruction[O] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      sys.invoke(operation)(input)
  }


  def pure[A](a: A): Free[Instruction, A] =
    Free.pure(a)

  def expr[A : Codec](mlExpr: MLExpr[A]): Free[Instruction, A] =
    Free.liftF[Instruction, A](Ex(mlExpr))

  def operation[I, O](operation: Operation[I, O], input: I): Free[Instruction, O] =
    Free.liftF[Instruction, O](Op(operation, input))

}
