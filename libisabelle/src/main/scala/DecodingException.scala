package edu.tum.cs.isabelle

import edu.tum.cs.isabelle.api.XML

import acyclic.file

/**
 * Error case of [[XMLResult]] as an exception.
 *
 * When decoding an [[edu.tum.cs.isabelle.api.XML.Tree XML tree]]
 * sent from the prover fails, this exception is fed into the corresponding
 * [[Observer observer]].
 */
case class DecodingException(msg: String, body: XML.Body) extends RuntimeException(msg)
