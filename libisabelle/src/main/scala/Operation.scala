package edu.tum.cs.isabelle

import isabelle.XML

object Operation {

  def fromEncodeDecode[I, O](name0: String, encode: XML.Encode.T[I], decode: XML.Decode.T[O]): Operation[I, O] =
    new Operation[I, O] {
      val name = name0
      def toProver(in: I) = List(encode(in))
      def fromProver(out: XML.Body) =
        try { Right(decode(out)) }
        catch {
          case ex: XML.Error => Left(ex)
        }
    }


  val Hello = fromEncodeDecode("hello", XML.Encode.string, XML.Decode.string)

}

trait Operation[I, O] {
  val name: String
  def toProver(in: I): List[XML.Body]
  def fromProver(out: XML.Body): Either[XML.Error, O]
}
