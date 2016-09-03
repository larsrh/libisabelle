package info.hupel.isabelle

import scala.concurrent._

import cats.free.Free

sealed trait Instruction[A] {
  def run(sys: System, thyName: String)(implicit ec: ExecutionContext): Future[ProverResult[A]]
}

object Instruction {

  private[isabelle] case class Ex[A](expr: ml.Expr[A])(implicit A: Codec[A]) extends Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      expr.eval(sys, thyName)
  }

  private[isabelle] case class Op[I, O](operation: Operation[I, O], input: I) extends Instruction[O] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      sys.invoke(operation)(input)
  }

}
