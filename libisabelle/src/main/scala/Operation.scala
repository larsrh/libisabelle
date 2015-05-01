package edu.tum.cs.isabelle

import scala.collection.JavaConverters._

import isabelle._

sealed abstract class Observer[T]

object Observer {

  case class Success[T](t: Exn.Result[T]) extends Observer[T]
  case class Failure[T](error: Exception) extends Observer[T]
  case class More[T](step: Prover.Message => Observer[T], done: XML.Tree => Observer[T]) extends Observer[T]

  def ignoreStep[T](done: XML.Tree => Observer[T]): Observer[T] = {
    lazy val observer: Observer[T] = More(_ => observer, done)
    observer
  }

  def decodeWith[O](fromProver: Codec[O])(tree: XML.Tree): Observer[O] =
    Codec.exnResult(fromProver).decode(tree) match {
      case Left((err, body)) => Observer.Failure(new DecodingException(err, body))
      case Right(o) => Observer.Success(o)
    }

}


object Operation {

  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    simple(name, Codec[I], Codec[O])

  val Hello = implicitly[String, String]("hello")
  val UseThys = implicitly[List[String], Unit]("use_thys")

  protected[isabelle] val UseThys_Java =
    Operation.simple("use_thys",
      Codec[List[String]].transform[java.util.List[String]](_.asJava, _.asScala.toList),
      Codec[Unit].transform[Void](_ => null, _ => ()))

  def simple[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]): Operation[I, O] =
    Operation(name, toProver, Observer.ignoreStep[O](Observer.decodeWith(fromProver)))

}

case class Operation[I, O](name: String, toProver: Codec[I], observer: Observer[O]) {
  def encode(i: I): XML.Tree = toProver.encode(i)
}
