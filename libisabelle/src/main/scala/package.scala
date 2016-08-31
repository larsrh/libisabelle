package info.hupel

import scala.concurrent._
import scala.util._

import cats.free.Free

import scalatags.Text

import info.hupel.isabelle.api.XML

package object isabelle {

  /**
   * The result type for [[Codec#decode decoding values]] from
   * [[info.hupel.isabelle.api.XML.Tree XML trees]]. Failure values
   * should contain an error message and a list of erroneous trees.
   */
  type XMLResult[+A] = Either[(String, XML.Body), A]

  type HTML = Text.TypedTag[String]

  type Program[A] = Free[MLProg.Instruction, A]

}
