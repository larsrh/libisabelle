package edu.tum.cs.isabelle

import isabelle.XML


object XMLCodec {

  private def fromIsabelle[A](enc: XML.Encode.T[A], dec: XML.Decode.T[A]): XMLCodec[A] =
    new XMLCodec[A] {
      def encode(a: A) = enc(a)
      def decode(xml: XML.Body) =
        try { Right(dec(xml)) }
        catch {
          case ex: XML.Error => Left(ex)
        }
    }


  val String: XMLCodec[String] = fromIsabelle(XML.Encode.string, XML.Decode.string)

}

trait XMLCodec[T] { self =>

  def encode(t: T): XML.Body
  def decode(xml: XML.Body): Either[XML.Error, T]

  def xmap[U](f: T => U, g: U => T): XMLCodec[U] = new XMLCodec[U] {
    def encode(u: U) = self.encode(g(u))
    def decode(xml: XML.Body) = self.decode(xml).right.map(f)
  }

}
