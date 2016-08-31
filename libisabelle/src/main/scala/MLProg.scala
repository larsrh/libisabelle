package info.hupel.isabelle

import scala.concurrent._

import cats.arrow.FunctionK
import cats.free.Free
import cats.instances.future._

import info.hupel.isabelle.ffi.MLExpr

object MLProg {

  sealed trait Instruction[A] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext): Future[ProverResult[A]]
  }

  private case class Ex[A](expr: MLExpr[A], typ: String)(implicit A: Codec[A]) extends Instruction[XMLResult[A]] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      expr.eval(sys, typ, expr, thyName)
  }

  private case class Op[I, O](operation: Operation[I, O], input: I) extends Instruction[O] {
    def run(sys: System, thyName: String)(implicit ec: ExecutionContext) =
      sys.invoke(operation)(input)
  }

  def interpreter(sys: System, thyName: String)(implicit ec: ExecutionContext): FunctionK[Instruction, Future] = new FunctionK[Instruction, Future] {
    def apply[A](instruction: Instruction[A]): Future[A] =
      instruction.run(sys, thyName).map(_.unsafeGet)
  }

  def run[A](prog: Free[Instruction, A], sys: System, thyName: String)(implicit ec: ExecutionContext): Future[A] =
    prog.foldMap(interpreter(sys, thyName))


  def pure[A](a: A): Free[Instruction, A] =
    Free.pure(a)

  def expr[A : Codec](mlExpr: MLExpr[A], typ: String): Free[Instruction, XMLResult[A]] =
    Free.liftF[Instruction, XMLResult[A]](Ex(mlExpr, typ))

  def unsafeExpr[A : Codec](mlExpr: MLExpr[A], typ: String): Free[Instruction, A] =
    expr(mlExpr, typ) map {
      case Left((err, body)) => throw DecodingException(err, body)
      case Right(o) => o
    }

  def operation[I, O](operation: Operation[I, O], input: I): Free[Instruction, O] =
    Free.liftF[Instruction, O](Op(operation, input))

}
