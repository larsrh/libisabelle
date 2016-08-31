package info.hupel.isabelle.ffi

import scala.concurrent._
import scala.math.BigInt

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML
import info.hupel.isabelle.ffi.types._
import info.hupel.isabelle.pure.Term

sealed abstract class MLExpr[A] {

  def eval(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[A]] =
    sys.invoke(MLExpr.EvalMLExpr)((Codec[A].mlType, this, thyName)).map(_.map { tree =>
      Codec[A].decode(tree) match {
        case Left((err, body)) => throw DecodingException(err, body)
        case Right(o) => o
      }
    })

  def check(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[Option[String]]] =
    sys.invoke(MLExpr.CheckMLExpr)((Codec[A].mlType, this, thyName))

  def toProg(implicit A: Codec[A]): Program[A] = MLProg.expr(this)

}

object MLExpr {

  implicit class MLExprFunOps[B, C](fun: MLExpr[B => C]) {
    def apply(arg: MLExpr[B]): MLExpr[C] =
      App(fun, arg)
    def apply(b: B)(implicit B: Codec[B]): MLExpr[C] =
      apply(value(b))
    def liftTry: MLExpr[B => Option[C]] =
      uncheckedLiteral[(B => C) => B => Option[C]]("try")(fun)
  }

  private case class Lit[A](text: String) extends MLExpr[A]
  private case class App[T, U](f: MLExpr[T => U], x: MLExpr[T]) extends MLExpr[U]
  private case class Val[T](t: T)(implicit T: Codec[T]) extends MLExpr[T] {
    def encode = Codec[(String, XML.Tree)].encode((T.mlType, T.encode(t)))
  }

  def value[T](t: T)(implicit T: Codec[T]): MLExpr[T] = Val[T](t)
  def uncheckedLiteral[A](text: String): MLExpr[A] = Lit[A](text)


  private implicit lazy val mlExprCodec: Codec[MLExpr[_]] = new Codec.Variant[MLExpr[_]]("FFI.ml_expr") {
    val mlType = "FFI.ml_expr"
    protected def dec(idx: Int) = None
    protected def enc(prog: MLExpr[_]) = prog match {
      case Lit(text) => (0, Codec[String].encode(text))
      case App(f, x) => (1, (mlExprCodec tuple mlExprCodec).encode((f, x)))
      case v: Val[_] => (2, v.encode)
    }
  }

  private val EvalMLExpr = Operation.implicitly[(String, MLExpr[_], String), XML.Tree]("eval_ml_expr")
  private val CheckMLExpr = Operation.implicitly[(String, MLExpr[_], String), Option[String]]("check_ml_expr")

}
