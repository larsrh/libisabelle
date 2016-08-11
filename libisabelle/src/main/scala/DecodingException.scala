package info.hupel.isabelle

import info.hupel.isabelle.api.XML

import acyclic.file

/**
 * Error case of [[XMLResult]] as an exception.
 *
 * When decoding an [[info.hupel.isabelle.api.XML.Tree XML tree]]
 * sent from the prover fails, this exception is fed into the corresponding
 * [[Observer observer]].
 */
final case class DecodingException(msg: String, body: XML.Body) extends RuntimeException(msg)
