package edu.tum.cs.isabelle

import scala.concurrent._

import acyclic.file

class CancellableFuture[T](private[isabelle] val promise: Promise[T], private[isabelle] val doCancel: () => Unit) {
  val future: Future[T] = promise.future

  def cancel(): Unit = {
    doCancel()
    promise.tryFailure(new CancellationException())
    ()
  }
}
