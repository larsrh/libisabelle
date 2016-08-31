package info.hupel.isabelle.ffi

import scala.concurrent._
import scala.math.BigInt

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML
import info.hupel.isabelle.ffi.types._
import info.hupel.isabelle.pure.Term

sealed abstract class MLExpr[A] {

  def eval(sys: System, typ: String, prog: MLExpr[A], thyName: String)(implicit A: Codec[A], ec: ExecutionContext): Future[ProverResult[XMLResult[A]]] =
    sys.invoke(MLExpr.EvalMLExpr)((typ, this, thyName)).map(_.map(Codec[A].decode))

}

object MLExpr {

  private case class Lit[A](name: String) extends MLExpr[A]
  private case class App[T, U](f: MLExpr[T => U], x: MLExpr[T]) extends MLExpr[U]
  private case class Val[T](mlType: String, t: T)(implicit T: Codec[T]) extends MLExpr[T] {
    def encode = Codec[(String, XML.Tree)].encode((mlType, T.encode(t)))
  }


  def int(n: BigInt): MLExpr[BigInt] =
    Val[BigInt]("int", n)

  def string(s: String): MLExpr[String] =
    Val[String]("string", s)

  def term(t: Term): MLExpr[Term] =
    Val[Term]("term", t)

  def getTheory(name: String): MLExpr[Theory] =
    App(Lit[String => Theory]("Thy_Info.get_theory"), string(name))

  def initGlobal(thy: MLExpr[Theory]): MLExpr[Context] =
    App(Lit[Theory => Context]("Proof_Context.init_global"), thy)

  def tryApp[T, U](f: MLExpr[T => U], x: MLExpr[T]): MLExpr[Option[U]] =
    App(App(Lit[(T => U) => T => Option[U]]("try"), f), x)

  def the[T](expr: MLExpr[Option[T]]): MLExpr[T] =
    App(Lit[Option[T] => T]("the"), expr)

  def parseTerm(ctxt: MLExpr[Context], term: String): MLExpr[Option[Term]] =
    tryApp(
      App(Lit[Context => String => Term]("Syntax.parse_term"), ctxt),
      string(term)
    )

  def checkTerm(ctxt: MLExpr[Context], t: Term): MLExpr[Option[Term]] =
    tryApp(
      App(Lit[Context => Term => Term]("Syntax.check_term"), ctxt),
      term(t)
    )

  def readTerm(ctxt: MLExpr[Context], term: String): MLExpr[Option[Term]] =
    tryApp(
      App(Lit[Context => String => Term]("Syntax.read_term"), ctxt),
      string(term)
    )

  private implicit lazy val mlExprCodec: Codec[MLExpr[_]] = new Codec.Variant[MLExpr[_]]("FFI.ml_expr") {
    protected def dec(idx: Int) = None
    protected def enc(prog: MLExpr[_]) = prog match {
      case Lit(name) => (0, Codec[String].encode(name))
      case App(f, x) => (1, (mlExprCodec tuple mlExprCodec).encode((f, x)))
      case v: Val[_] => (2, v.encode)
    }
  }

  private val EvalMLExpr = Operation.implicitly[(String, MLExpr[_], String), XML.Tree]("eval_ml_expr")

}
