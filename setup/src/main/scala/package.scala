package edu.tum.cs.isabelle

import scala.concurrent.{Future, Promise}

import scalaz.concurrent.Task

/**
 * Tools for setting up an Isabelle installation. Most functions in this
 * package have some effect on the local file system and may download content
 * from the Internet.
 */
package object setup {

  implicit class TaskOps[T](task: Task[T]) {
    def toScalaFuture: Future[T] = {
      val promise = Promise[T]
      task.runAsync { res =>
        res.fold(promise.failure, promise.success)
        ()
      }
      promise.future
    }
  }

}
