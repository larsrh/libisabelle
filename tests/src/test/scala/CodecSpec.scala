package edu.tum.cs.isabelle.tests

import scala.math.BigInt

import org.specs2.{ScalaCheck, Specification}

import org.scalacheck._
import org.scalacheck.Prop.forAll

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._

class CodecSpec extends Specification with ScalaCheck { def is = s2"""

  Round-trip property of Codecs

  Values can be converted
    of type Unit                      ${propCodec[Unit]}
    of type Boolean                   ${propCodec[Boolean]}
    of type BigInt                    ${propCodec[BigInt]}
    of type String                    ${propCodec[String]}
    of type (BigInt, BigInt)          ${propCodec[(BigInt, BigInt)]}
    of type (String, Unit)            ${propCodec[(String, Unit)]}
    of type List[Unit]                ${propCodec[List[Unit]]}
    of type List[BigInt]              ${propCodec[List[BigInt]]}
    of type List[String]              ${propCodec[List[String]]}
    of type List[List[String]]        ${propCodec[List[List[String]]]}
    of type List[(BigInt, BigInt)]    ${propCodec[List[(BigInt, BigInt)]]}
    of type (BigInt, List[BigInt])    ${propCodec[(BigInt, List[BigInt])]}
    of type Option[BigInt]            ${propCodec[Option[BigInt]]}
    of type Option[List[BigInt]]      ${propCodec[Option[List[BigInt]]]}
    of type List[Option[BigInt]]      ${propCodec[List[Option[BigInt]]]}
    of type Either[String, BigInt]    ${propCodec[Either[String, BigInt]]}
  """

  def propCodec[A : Codec : Arbitrary] = forAll { (a: A) =>
    Codec[A].decode(Codec[A].encode(a)) must beRight(a)
  }

}
