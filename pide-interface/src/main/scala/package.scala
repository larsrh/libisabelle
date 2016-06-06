package edu.tum.cs.isabelle

import java.util.concurrent.{AbstractExecutorService, TimeUnit}
import java.util.Collections

import scala.concurrent.{ExecutionContext, ExecutionContextExecutorService}

import acyclic.file

/**
 * Minimal API for managing some Isabelle version. It is centered around the
 * notion of an [[Environment environment]], which captures the base
 * functionality of an Isabelle process, e.g. starting and stopping an
 * instance. API clients should go through the higher-level
 * [[edu.tum.cs.isabelle.setup.Setup setup]] and
 * [[edu.tum.cs.isabelle.System system]] interfaces.
 */
package object api {

  type Properties = List[(String, String)]

  type Markup = (String, Properties)

  private[isabelle] final implicit class ExecutionContextOps(ec: ExecutionContext) {

    // based on <https://gist.github.com/viktorklang/5245161>, see CREDITS
    def toExecutorService: ExecutionContextExecutorService = ec match {
      case eces: ExecutionContextExecutorService => eces
      case other => new AbstractExecutorService with ExecutionContextExecutorService {
        override def prepare(): ExecutionContext = other
        override def isShutdown = false
        override def isTerminated = false
        override def shutdown() = ()
        override def shutdownNow() = Collections.emptyList[Runnable]
        override def execute(runnable: Runnable): Unit = other execute runnable
        override def reportFailure(t: Throwable): Unit = other reportFailure t
        override def awaitTermination(length: Long, unit: TimeUnit): Boolean = false
      }
    }

  }

}
