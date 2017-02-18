package info.hupel.isabelle.tests

import scala.math.BigInt

import org.specs2.{ScalaCheck, Specification}
import org.specs2.specification.core.Env

import org.scalacheck._
import org.scalacheck.Prop.forAll

import info.hupel.isabelle._
import info.hupel.isabelle.api.XML

class CodecSpec(val specs2Env: Env) extends Specification with BasicSetup with ScalaCheck { def is = s2"""

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

  def propCodec[A : Codec : Arbitrary] = properties(new Properties("props") {
    property("encode/decode") = forAll { (a: A) =>
      Codec[A].decode(Codec[A].encode(a)) must beRight(a)
    }

    property("YXML") = forAll { (a: A) =>
      val encoded = Codec[A].encode(a)
      XML.fromYXML(encoded.toYXML) must be_===(encoded)
    }
  }) ^ bt ^ br

}
