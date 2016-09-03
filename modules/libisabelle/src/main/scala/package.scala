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

  type Program[A] = Free[Instruction, A]

  object Program {

    def pure[A](a: A): Program[A] =
      Free.pure(a)

    def expr[A : Codec](mlExpr: ml.Expr[A]): Program[A] =
      Free.liftF[Instruction, A](Instruction.Ex(mlExpr))

    def operation[I, O](operation: Operation[I, O], input: I): Program[O] =
      Free.liftF[Instruction, O](Instruction.Op(operation, input))

  }

}
