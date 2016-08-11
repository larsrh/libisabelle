package info.hupel

import scala.concurrent._
import scala.util._

import info.hupel.isabelle.api.XML

import acyclic.file

package object isabelle {

  /**
   * The result type for [[Codec#decode decoding values]] from
   * [[info.hupel.isabelle.api.XML.Tree XML trees]]. Failure values
   * should contain an error message and a list of erroneous trees.
   */
  type XMLResult[+A] = Either[(String, XML.Body), A]

}
