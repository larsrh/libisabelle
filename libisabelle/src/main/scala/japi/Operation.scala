package edu.tum.cs.isabelle.japi

import edu.tum.cs.isabelle._

import isabelle.XML

abstract class AbstractOperation[I, O](val name: String) extends Operation[I, O] {

  @throws(classOf[XML.Error])
  def tryFromProver(out: XML.Body): O
  
  final def fromProver(out: XML.Body): Either[XML.Error, O] =
    try { Right(tryFromProver(out)) }
    catch {
      case ex: XML.Error => Left(ex)
    }

}
