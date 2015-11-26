package edu.tum.cs

import scala.concurrent._
import scala.util._

import edu.tum.cs.isabelle.api.XML

import acyclic.file

package object isabelle {

  type Indexname = (String, BigInt)
  type Sort = List[String]

  /**
   * The result type for [[Codec#decode decoding values]] from
   * [[edu.tum.cs.isabelle.api.XML.Tree XML trees]]. Failure values
   * should contain an error message and a list of erroneous trees.
   */
  type XMLResult[+A] = Either[(String, XML.Body), A]

  implicit class FutureOps[T](val future: Future[T]) extends AnyVal {
    def flatMapC[U](f: T => CancellableFuture[U])(implicit ec: ExecutionContext): CancellableFuture[U] = {
      val u = future.map(f)
      val promise = Promise[U]
      u.onComplete {
        case Success(ct) => promise.completeWith(ct.future)
        case Failure(exn) => promise.failure(exn)
      }
      new CancellableFuture(promise, () => u.foreach(_.doCancel()))
    }
  }

}
