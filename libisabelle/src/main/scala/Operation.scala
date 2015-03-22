package edu.tum.cs.isabelle

import isabelle.XML

object Operation {

  def fromCodecs[I, O](name0: String, enc: XMLCodec[I], dec: XMLCodec[O]): Operation[I, O] =
    new Operation[I, O] {
      val name = name0
      def toProver(in: I) = List(enc.encode(in))
      def fromProver(out: XML.Body) = dec.decode(out)
    }

  val Hello = fromCodecs("hello", XMLCodec.String, XMLCodec.String)

}

trait Operation[I, O] {
  val name: String
  def toProver(in: I): List[XML.Body]
  def fromProver(out: XML.Body): Either[XML.Error, O]
}
