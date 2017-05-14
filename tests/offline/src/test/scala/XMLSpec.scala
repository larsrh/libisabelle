package info.hupel.isabelle.tests

import org.specs2.{ScalaCheck, Specification}
import org.specs2.scalacheck.Parameters
import org.specs2.specification.core.Env

import org.scalacheck.Prop.forAll

import info.hupel.isabelle.api.XML

class XMLSpec(val specs2Env: Env) extends Specification with BasicSetup with ScalaCheck { def is = s2"""

  Round-trip property of XML encoding

  XML can be encoded into YXML        $prop
  """

  implicit val params = Parameters(maxSize = 50, minTestsOk = 1000)

  def compress(body: XML.Body): XML.Body = body match {
    case XML.Text(t1) :: XML.Text(t2) :: tail => compress(XML.Text(t1 + t2) :: tail)
    case head :: tail => head :: compress(tail)
    case Nil => Nil
  }

  def compress(tree: XML.Tree): XML.Tree = tree match {
    case XML.Text(_) => tree
    case XML.Elem(markup, body) => XML.Elem(markup, compress(body.map(compress)))
  }

  lazy val prop = forAll { (tree: XML.Tree) =>
    XML.fromYXML(tree.toYXML) must be_===(compress(tree))
  }

}
