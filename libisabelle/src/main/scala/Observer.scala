package edu.tum.cs.isabelle

import edu.tum.cs.isabelle.api.XML

import acyclic.file

/**
 * Result from the prover.
 *
 * In the error case, a special
 * `[[edu.tum.cs.isabelle.Operation.ProverException ProverException]]` will be
 * provided.
 *
 * @see [[edu.tum.cs.isabelle.System#invoke]]
 */
trait ProverResult[+T]

object ProverResult {
  case class Success[+T](t: T) extends ProverResult[T]
  case class Failure(exn: Exception) extends ProverResult[Nothing]
}

/**
 * An iteratee-like structure consuming
 * [[edu.tum.cs.isabelle.api.XML.Tree XML trees]] and eventually producing an
 * output of type `T`, or an error.
 *
 * On a high level, this can be imagined like a function taking a list of
 * trees as an argument. In most cases, user code does not care about the
 * intermediate results. For that, combinators exist in the
 * [[Observer$ companion object]] and
 * `[[edu.tum.cs.isabelle.Operation$ Operation]]`.
 *
 * @see [[edu.tum.cs.isabelle.System#invoke]]
 * @see [[edu.tum.cs.isabelle.Operation]]
 */
sealed abstract class Observer[T]

/** Cases of [[Observer observers]] and combinators. */
object Observer {

  /**
   * Represents a final result from the prover, be it successful or failed.
   *
   * It is recommended that this case is not used for unexpected failures.
   *
   * For the precise error semantics, see
   * `[[edu.tum.cs.isabelle.System#invoke System#invoke]]`.
   */
  case class Success[T](t: ProverResult[T]) extends Observer[T]

  /**
   * Represents an unexpected failure during communication with the prover.
   *
   * It is recommended that this case is not used for routine error
   * conditions.
   *
   * For the precise error semantics, see
   * `[[edu.tum.cs.isabelle.System#invoke System#invoke]]`.
   */
  case class Failure[T](error: Exception) extends Observer[T]

  /**
   * An [[Observer observer]] waiting for more intermediate data (`step`) or
   * a final result (`done`).
   *
   * When receiving a final result, the returned new observer should be
   * either `[[Success]]` or `[[Failure]]`. This is not enforced, but not
   * doing so will most likely result in hanging
   * [[edu.tum.cs.isabelle.Operation operations]].
   */
  case class More[T](step: XML.Tree => Observer[T], done: XML.Tree => Observer[T]) extends Observer[T]

  /**
   * Combinator for producing an [[Observer observer]] which ignores
   * intermediate data.
   */
  def ignoreStep[T](done: XML.Tree => Observer[T]): Observer[T] = {
    lazy val observer: Observer[T] = More(_ => observer, done)
    observer
  }

}
