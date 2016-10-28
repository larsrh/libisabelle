package info.hupel.isabelle.ml

import scala.concurrent._
import scala.math.BigInt

import cats.Monad
import cats.free.Free

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML
import info.hupel.isabelle.pure.Term

trait Scope {

  sealed abstract class Expr[A] {

    private[isabelle] def eval(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[A]] =
      sys.invoke(Scope.EvalExpr)((A.mlType, this, thyName)).map(_.map(A.decodeOrThrow))

    private[isabelle] def opaqueEval[Repr](sys: System, thyName: String, conv: ml.Expr[A => Repr])(implicit A: Opaque[A], Repr: Codec[Repr], ec: ExecutionContext): Future[ProverResult[(BigInt, Repr)]] =
      sys.invoke(Scope.EvalOpaqueExpr)(((A.table, Repr.mlType, conv), this, thyName)).map(_.map { case (id, tree) =>
        (id, Repr.decodeOrThrow(tree))
      })

    private[isabelle] def rawPeek0[Repr : Codec](conv: ml.Expr[A => Repr])(implicit A: Opaque[A]): Program[(Ref[A], Repr)] =
      for {
        tuple <- Free.liftF[Instruction, (BigInt, Repr)](Instruction.OpaqueEx(this, conv))
        (id, repr) = tuple
      } yield (ml.Ref(id), repr)

    private[isabelle] def peek0[Repr : Codec, C](conv: ml.Expr[A => Repr], scoped: Scoped[A, Repr, C])(implicit A: Opaque[A]): Program[C] = {
      val scope = new ml.LocalScope

      for {
        tuple <- rawPeek0(conv)
        (ref, repr) = tuple
        res <- scoped(scope)(repr, scope.localize(ref.read)).prog
        _ <- ref.delete.toProg
      } yield res
    }

    def check(sys: System, thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[Option[String]]] =
      sys.invoke(Scope.CheckExpr)((Codec[A].mlType, this, thyName))

    def coerce[B]: Expr[B] = Expr.Coerce(this)

    protected[Scope] def enc: (Int, XML.Tree)

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

    private case class Lit[A](text: String) extends Expr[A] {
      protected[Scope] def enc = (0, Codec[String].encode(text))
    }
    private case class App[T, U](f: Expr[T => U], x: Expr[T]) extends Expr[U] {
      protected[Scope] def enc = (1, (Scope.exprCodec tuple Scope.exprCodec).encode((f, x)))
    }
    private case class Val[T](t: T)(implicit T: Codec[T]) extends Expr[T] {
      protected[Scope] def enc = (2, Codec[(String, XML.Tree)].encode((T.mlType, T.encode(t))))
    }
    private[isabelle] case class Global[T](e: ml.Expr[T]) extends Expr[T] {
      protected[Scope] def enc = e.enc
    }
    private case class Coerce[T, U](t: Expr[T]) extends Expr[U] {
      protected[Scope] def enc = t.enc
    }

    def value[T](t: T)(implicit T: Codec[T]): Expr[T] = Val[T](t)
    def uncheckedLiteral[A](text: String): Expr[A] = Lit[A](text)

  }

}

object Scope {

  private implicit lazy val exprCodec: Codec[Scope#Expr[_]] = new Codec.Variant[Scope#Expr[_]]("ML_Expr.ml_expr") {
    val mlType = "ML_Expr.ml_expr"
    protected def dec(idx: Int) = None
    protected def enc(expr: Scope#Expr[_]) = expr.enc
  }

  private val EvalExpr = Operation.implicitly[(String, Scope#Expr[_], String), XML.Tree]("eval_expr")
  private val EvalOpaqueExpr = Operation.implicitly[((String, String, Scope#Expr[_]), Scope#Expr[_], String), (BigInt, XML.Tree)]("eval_opaque_expr")
  private val CheckExpr = Operation.implicitly[(String, Scope#Expr[_], String), Option[String]]("check_expr")

}

class LocalScope extends Scope {

  type GlobalProgram[A] = info.hupel.isabelle.Program[A]
  val GlobalProgram = info.hupel.isabelle.Program

  class Program[A] private[LocalScope](private[isabelle] val prog: GlobalProgram[A]) {
    private def copy = this
    def map[B](f: A => B): Program[B] =
      new Program(prog map f)
    def flatMap[B](f: A => Program[B]): Program[B] =
      new Program(prog flatMap { a => f(a).prog })
  }

  object Program {
    implicit val localProgramMonad: Monad[Program] = new Monad[Program] {
      def pure[A](x: A) = new Program(Free.pure(x))
      def flatMap[A, B](fa: Program[A])(f: A => Program[B]) = fa flatMap f
      def tailRecM[A, B](a: A)(f: A => Program[Either[A,B]]) = localize {
        Monad[GlobalProgram].tailRecM(a) { a => f(a).prog }
      }
    }
  }

  def value[T](t: T)(implicit T: Codec[T]): Expr[T] = Expr.value(t)
  def uncheckedLiteral[A](text: String): Expr[A] = Expr.uncheckedLiteral(text)

  def localize[A](expr: ml.Expr[A]): Expr[A] = Expr.Global(expr)
  def localize[A](prog: GlobalProgram[A]): Program[A] = new Program(prog)

  implicit class LocalScopeExprOps[A](e: Expr[A]) {

    def toProg(implicit A: Codec[A]): Program[A] =
      localize(Free.liftF[Instruction, A](Instruction.Ex(e)))

    def rawPeek[Repr : Codec](conv: ml.Expr[A => Repr])(implicit A: Opaque[A]): Program[(Ref[A], Repr)] =
      localize(e.rawPeek0(conv))

    def peek[Repr : Codec, C](conv: ml.Expr[A => Repr])(scoped: Scoped[A, Repr, C])(implicit A: Opaque[A]): Program[C] =
      localize(e.peek0(conv, scoped))

  }

}

trait Scoped[A, Repr, B] {
  def apply(scope: LocalScope)(repr: Repr, ref: scope.Expr[A]): scope.Program[B]
}
