package edu.tum.cs.isabelle

import scala.collection.JavaConverters._

import edu.tum.cs.isabelle.api.Environment

object Operation {

  private def decodeWith[O](env: Environment, fromProver: Codec[O])(tree: env.XMLTree): env.Observer[O] =
    Codec.proverResult(fromProver).decode(env)(tree) match {
      case Left((err, body)) => env.Observer.Failure(new DecodingException(err, body))
      case Right(o) => env.Observer.Success(o)
    }

  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    simple(name, Codec[I], Codec[O])

  def simple[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]): Operation[I, O] =
    new Operation[I, O](name, toProver) {
      def observer(env: Environment): env.Observer[O] =
        env.Observer.ignoreStep[O](tree => decodeWith(env, fromProver)(tree))
    }

  val Hello = implicitly[String, String]("hello")
  val UseThys = implicitly[List[String], Unit]("use_thys")

}

abstract class Operation[I, O](val name: String, val toProver: Codec[I]) {
  def observer(env: Environment): env.Observer[O]
  final def encode(env: Environment)(i: I): env.XMLTree = toProver.encode(env)(i)
}
