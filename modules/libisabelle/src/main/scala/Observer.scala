package info.hupel.isabelle

import scala.util.control.NoStackTrace

import info.hupel.isabelle.api.XML

/**
 * Result from the prover.
 *
 * The error case additionally provides the input and the operation that have
 * been sent to the prover.
 *
 * @see [[info.hupel.isabelle.System#invoke]]
 */
sealed trait ProverResult[+T] {
  def unsafeGet: T = this match {
    case ProverResult.Success(t) => t
    case fail: ProverResult.Failure => throw fail
  }
  def toOption: Option[T] = this match {
    case ProverResult.Success(t) => Some(t)
    case _ => None
  }
  def map[U](f: T => U): ProverResult[U] = this match {
    case ProverResult.Success(t) => ProverResult.Success(f(t))
    case fail: ProverResult.Failure => fail
  }
}

object ProverResult {

  final case class Success[+T](t: T) extends ProverResult[T]

  final case class Failure private(operation: String, msg: String, input: Any) extends RuntimeException with ProverResult[Nothing] with NoStackTrace {
    def fullMessage =
      s"Prover error in operation $operation: $msg\nOffending input: $input"
  }

  private def failureCodec(operation: String, input: Any): Codec[Failure] = Codec.text[Failure](
    _.getMessage,
    str => Some(Failure(operation, str, input)),
    "exn"
  ).tagged("exn")

  private[isabelle] def resultCodec[O](codec: Codec[O], operation: String, input: Any): Codec[ProverResult[O]] = new Codec.Variant[ProverResult[O]]("Exn.result") {
    val mlType = "Exn.result"
    def enc(a: ProverResult[O]) = sys.error("impossible")
    def dec(idx: Int) = idx match {
      case 0 => Some(codec.decode(_).right.map(Success.apply))
      case 1 => Some(failureCodec(operation, input).decode(_))
      case _ => None
    }
  }

}

/**
 * An iteratee-like structure consuming
 * [[info.hupel.isabelle.api.XML.Tree XML trees]] and eventually producing an
 * output of type `T`, or an error.
 *
 * On a high level, this can be imagined like a function taking a list of
 * trees as an argument. In most cases, user code does not care about the
 * intermediate results. For that, combinators exist in the
 * [[Observer$ companion object]] and
 * `[[info.hupel.isabelle.Operation$ Operation]]`.
 *
 * @see [[info.hupel.isabelle.System#invoke]]
 * @see [[info.hupel.isabelle.Operation]]
 */
sealed abstract class Observer[+T] {
  def map[U](f: T => U): Observer[U] = this match {
    case Observer.Success(result) => Observer.Success(result map f)
    case fail: Observer.Failure => fail
    case Observer.More(step, done) => Observer.More(step.andThen(_.map(f)), done.andThen(_.map(f)))
  }
}

/** Cases of [[Observer observers]] and combinators. */
object Observer {

  /**
   * Represents a final result from the prover, be it successful or failed.
   *
   * It is recommended that this case is not used for unexpected failures.
   *
   * For the precise error semantics, see
   * `[[info.hupel.isabelle.System#invoke System#invoke]]`.
   */
  final case class Success[+T](t: ProverResult[T]) extends Observer[T]

  /**
   * Represents an unexpected failure during communication with the prover.
   *
   * It is recommended that this case is not used for routine error
   * conditions.
   *
   * For the precise error semantics, see
   * `[[info.hupel.isabelle.System#invoke System#invoke]]`.
   */
  final case class Failure(error: Exception) extends Observer[Nothing]

  /**
   * An [[Observer observer]] waiting for more intermediate data (`step`) or
   * a final result (`done`).
   *
   * When receiving a final result, the returned new observer should be
   * either `[[Success]]` or `[[Failure]]`. This is not enforced, but not
   * doing so will most likely result in hanging
   * [[info.hupel.isabelle.Operation operations]].
   */
  final case class More[+T](step: XML.Tree => Observer[T], done: XML.Tree => Observer[T]) extends Observer[T]

  /**
   * Combinator for producing an [[Observer observer]] which ignores
   * intermediate data.
   */
  def ignoreStep[T](done: XML.Tree => Observer[T]): Observer[T] = {
    lazy val observer: Observer[T] = More(_ => observer, done)
    observer
  }

}
