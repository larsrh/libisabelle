package edu.tum.cs.isabelle

import scala.concurrent.ExecutionContext

object defaults {
  implicit lazy val isabelleExecutionContext: ExecutionContext =
    isabelle.Future.execution_context
}
