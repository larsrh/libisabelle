package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future}
import scala.math.BigInt

import edu.tum.cs.isabelle.pure._

import acyclic.file

private class ListTypeable[T : Typeable] extends Typeable[List[T]] {
  def typ: Typ = Type("List.list", List(Typeable.typ[T]))
}

trait LowPriorityImplicits {

  implicit def listTypeable[T : Typeable]: Typeable[List[T]] = new ListTypeable[T]

}

package object hol extends LowPriorityImplicits {

  private val MkInt = Operation.implicitly[BigInt, Term]("mk_int")
  private val MkList = Operation.implicitly[(Typ, List[Term]), Term]("mk_list")

  implicit def bigIntTypeable: Embeddable[BigInt] = new Embeddable[BigInt] {
    def typ: Typ = Type("Int.int", Nil)
    def embed(thy: Theory, t: BigInt)(implicit ec: ExecutionContext): Future[Term] =
      thy.system.invoke(MkInt)(t) map {
        case ProverResult.Failure(exn) => throw exn
        case ProverResult.Success(t) => t
      }
  }

  implicit def boolTypeable: Embeddable[Boolean] = new Embeddable[Boolean] {
    def typ: Typ = Type("HOL.bool", Nil)
    def embed(thy: Theory, t: Boolean)(implicit ec: ExecutionContext): Future[Term] = Future.successful {
      t match {
        case true => Const("HOL.True", typ)
        case false => Const("HOL.False", typ)
      }
    }
  }

  implicit def listEmbeddable[T : Embeddable]: Embeddable[List[T]] = new ListTypeable[T] with Embeddable[List[T]] {
    def embed(thy: Theory, ts: List[T])(implicit ec: ExecutionContext): Future[Term] =
      Future.traverse(ts)(Embeddable[T].embed(thy, _)) flatMap { ts =>
        thy.system.invoke(MkList)((Typeable.typ[T], ts))
      } map {
        case ProverResult.Failure(exn) => throw exn
        case ProverResult.Success(t) => t
      }
  }

}
