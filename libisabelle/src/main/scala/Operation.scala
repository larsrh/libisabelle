package edu.tum.cs.isabelle

import isabelle._

object Operation {

  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    Operation(name, Codec[I], Codec[O])

  val Hello = implicitly[String, String]("hello")

}

case class Operation[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]) {
  def encode(i: I): XML.Tree = toProver.encode(i)
  def decode(xml: XML.Tree): Result[O] =
    Codec.exnResult(fromProver).decode(xml).right.map(Exn.release)
}
