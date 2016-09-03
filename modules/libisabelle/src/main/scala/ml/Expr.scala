package info.hupel.isabelle.ml

import scala.concurrent._
import scala.math.BigInt

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML
import info.hupel.isabelle.pure.Term

sealed abstract class Expr[A] {

  def eval(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[A]] =
    sys.invoke(Expr.EvalExpr)((Codec[A].mlType, this, thyName)).map(_.map { tree =>
      Codec[A].decode(tree) match {
        case Left((err, body)) => throw DecodingException(err, body)
        case Right(o) => o
      }
    })

  def check(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[Option[String]]] =
    sys.invoke(Expr.CheckExpr)((Codec[A].mlType, this, thyName))

  def coerce[B]: Expr[B] = Expr.Coerce(this)

  def toProg(implicit A: Codec[A]): Program[A] = Program.expr(this)

}

object Expr {

  implicit class ExprFunOps[B, C](fun: Expr[B => C]) {
    def apply(arg: Expr[B]): Expr[C] =
      App(fun, arg)
    def apply(b: B)(implicit B: Codec[B]): Expr[C] =
      apply(value(b))
    def liftTry: Expr[B => Option[C]] =
      uncheckedLiteral[(B => C) => B => Option[C]]("try")(fun)
  }

  private case class Lit[A](text: String) extends Expr[A]
  private case class App[T, U](f: Expr[T => U], x: Expr[T]) extends Expr[U]
  private case class Val[T](t: T)(implicit T: Codec[T]) extends Expr[T] {
    def encode = Codec[(String, XML.Tree)].encode((T.mlType, T.encode(t)))
  }
  private case class Coerce[T, U](t: Expr[T]) extends Expr[U]

  def value[T](t: T)(implicit T: Codec[T]): Expr[T] = Val[T](t)
  def uncheckedLiteral[A](text: String): Expr[A] = Lit[A](text)


  private implicit lazy val exprCodec: Codec[Expr[_]] = new Codec.Variant[Expr[_]]("ML_Expr.ml_expr") {
    val mlType = "ML_Expr.ml_expr"
    protected def dec(idx: Int) = None
    protected def enc(prog: Expr[_]) = prog match {
      case Lit(text) => (0, Codec[String].encode(text))
      case App(f, x) => (1, (exprCodec tuple exprCodec).encode((f, x)))
      case v: Val[_] => (2, v.encode)
      case Coerce(e) => enc(e)
    }
  }

  private val EvalExpr = Operation.implicitly[(String, Expr[_], String), XML.Tree]("eval_expr")
  private val CheckExpr = Operation.implicitly[(String, Expr[_], String), Option[String]]("check_expr")

}
