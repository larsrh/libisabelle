package edu.tum.cs.isabelle

import scala.concurrent.{CancellationException, Future, Promise}

class CancellableFuture[T](promise: Promise[T], doCancel: Unit => Unit) {
  val future: Future[T] = promise.future

  def cancel(): Unit = {
    doCancel(())
    promise.tryFailure(new CancellationException())
    ()
  }
}
