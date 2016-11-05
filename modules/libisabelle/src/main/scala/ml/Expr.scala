package info.hupel.isabelle.ml

import scala.concurrent._
import scala.math.BigInt

import cats.Monad
import cats.free.Free

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML
import info.hupel.isabelle.pure.Term

sealed abstract class Expr[A] {

  private[isabelle] def eval(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[A]] =
    sys.invoke(Expr.Eval)((A.mlType, this, thyName)).map(_.map(A.decodeOrThrow))

  private[isabelle] def opaqueEval[Repr](sys: System, thyName: String, conv: ml.Expr[A => Repr])(implicit A: Opaque[A], Repr: Codec[Repr], ec: ExecutionContext): Future[ProverResult[(BigInt, Repr)]] =
    sys.invoke(Expr.EvalOpaque)(((A.table, Repr.mlType, conv), this, thyName)).map(_.map { case (id, tree) =>
      (id, Repr.decodeOrThrow(tree))
    })

  def toProg(implicit A: Codec[A]): Program[A] =
    Program.expr(this)

  def rawPeek[Repr : Codec](conv: Expr[A => Repr])(implicit A: Opaque[A]): Program[(Ref[A], Repr)] =
    for {
      tuple <- Free.liftF[Instruction, (BigInt, Repr)](Instruction.OpaqueEx(this, conv))
      (id, repr) = tuple
    } yield (Ref(id), repr)

  def peek[Repr : Codec, C](conv: Expr[A => Repr])(f: (Repr, Expr[A]) => Program[C])(implicit A: Opaque[A]): Program[C] = {
    for {
      tuple <- rawPeek(conv)
      (ref, repr) = tuple
      res <- f(repr, ref.read)
      _ <- ref.delete.toProg
    } yield res
  }

  def check(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[Option[String]]] =
    sys.invoke(Expr.Check)((Codec[A].mlType, this, thyName))

  def coerce[B]: Expr[B] = Expr.Coerce(this)

}

object Expr {

  implicit class ExprFunOps[B, C](fun: Expr[B => C]) {
    def apply(arg: Expr[B]): Expr[C] =
      App(fun, arg)
    def apply(b: B)(implicit B: Codec[B]): Expr[C] =
      apply(value(b))
    def liftTry: Expr[B => Option[C]] =
      uncheckedLiteral[(B => C) => B => Option[C]]("try")(fun)
    def andThen[D](fun2: Expr[C => D]): Expr[B => D] =
      uncheckedLiteral[(B => C) => (C => D) => B => D]("(fn f => fn g => g o f)")(fun)(fun2)
  }

  private case class Lit[A](text: String) extends Expr[A]
  private case class App[T, U](f: Expr[T => U], x: Expr[T]) extends Expr[U]
  private case class Val[T](t: T)(implicit val T: Codec[T]) extends Expr[T]
  private case class Coerce[T, U](t: Expr[T]) extends Expr[U]

  def value[T](t: T)(implicit T: Codec[T]): Expr[T] = Val[T](t)
  def uncheckedLiteral[A](text: String): Expr[A] = Lit[A](text)

  private implicit lazy val exprCodec: Codec[Expr[_]] = new Codec.Variant[Expr[_]]("ML_Expr.ml_expr") {
    val mlType = "ML_Expr.ml_expr"
    protected def dec(idx: Int) = None
    protected def enc(expr: Expr[_]) = expr match {
      case Lit(text) => (0, Codec[String].encode(text))
      case App(f, x) => (1, (exprCodec tuple exprCodec).encode((f, x)))
      case v: Val[_] => (2, Codec[(String, XML.Tree)].encode((v.T.mlType, v.T.encode(v.t))))
      case Coerce(t) => enc(t)
    }
  }

  private val Eval = Operation.implicitly[(String, Expr[_], String), XML.Tree]("eval_expr")
  private val EvalOpaque = Operation.implicitly[((String, String, Expr[_]), Expr[_], String), (BigInt, XML.Tree)]("eval_opaque_expr")
  private val Check = Operation.implicitly[(String, Expr[_], String), Option[String]]("check_expr")

}
