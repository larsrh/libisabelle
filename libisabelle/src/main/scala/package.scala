package edu.tum.cs.isabelle

import scala.concurrent._
import scala.util._

import edu.tum.cs.isabelle.api.Environment

import acyclic.file

/**
 * Error case of [[XMLResult]] as an exception.
 *
 * When decoding an [[edu.tum.cs.isabelle.api.Environment#XMLTree XML tree]]
 * sent from the prover fails, this exception is fed into the corresponding
 * [[edu.tum.cs.isabelle.api.Environment#Observer observer]].
 */
case class DecodingException(msg: String, body: Environment#XMLBody) extends RuntimeException(msg)

object `package` {

  type Indexname = (String, BigInt)
  type Sort = List[String]

  /**
   * The result type for [[Codec#decode decoding values]] from
   * [[edu.tum.cs.isabelle.api.Environment#XMLTree XML trees]]. Failure values
   * should contain an error message and a list of erroneous trees.
   */
  type XMLResult[+A] = Either[(String, Environment#XMLBody), A]

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
