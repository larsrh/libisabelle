package edu.tum.cs.isabelle.setup

import scala.concurrent.{Future, Promise}

import scalaz.concurrent.Task

object `package` {

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
