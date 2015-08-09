package edu.tum.cs.isabelle

import scala.collection.JavaConverters._

import edu.tum.cs.isabelle.api.Environment

/** Combinators for creating [[Operation operations]] and basic operations. */
object Operation {

  private def decodeWith[O](env: Environment, fromProver: Codec[O])(tree: env.XMLTree): env.Observer[O] =
    Codec.proverResult(fromProver).decode(env)(tree) match {
      case Left((err, body)) => env.Observer.Failure(new DecodingException(err, body))
      case Right(o) => env.Observer.Success(o)
    }

  /** Create a [[simple]] observer using implicit [[Codec codecs]]. */
  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    simple(name, Codec[I], Codec[O])

  /**
   * Creates an observer which ignores any intermediate data, waits for the
   * final result and immediately attempts to decode it with a given
   * [[Codec codec]].
   */
  def simple[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]): Operation[I, O] =
    new Operation[I, O](name, toProver) {
      def observer(env: Environment): env.Observer[O] =
        env.Observer.ignoreStep[O](tree => decodeWith(env, fromProver)(tree))
    }

  /** A simple ping operation. */
  val Hello = implicitly[String, String]("hello")

  /**
   * Load a list of theory files in the prover, specified by their paths.
   *
   * The format of the paths is rather peculiar: They are relative to the
   * working directory at the time the [[System.create system was created]],
   * but do not include the extension `.thy`. Theories which are already part
   * of the [[edu.tum.cs.isabelle.api.Environment#Configuration configuration]]
   * should not be loaded again.
   */
  val UseThys = implicitly[List[String], Unit]("use_thys")

}

/**
 * Description of an atomic interaction with the prover.
 *
 * An operation can be roughly seen as a remote procedure call: a method name,
 * an input argument of type `I` and an output value of type `O`, potentially
 * accompanied by a stream of auxiliary data. (For almost all use cases, the
 * latter is irrelevant.)
 *
 * Data is transferred between JVM and the prover using
 * [[edu.tum.cs.isabelle.api.Environment#XMLTree XML trees]]. To convert
 * between typed data and their XML representation, [[Codec codecs]] are used.
 * Each operation has at least a codec for its input argument. Most operations
 * will also use a codec for their output value.
 *
 * In the most general case, an operation listens for a stream of output from
 * the prover using an
 * [[edu.tum.cs.isabelle.api.Environment#Observer observer]], comparable to
 * iteratees.
 *
 * Operations can most easily be constructed with the
 * `[[Operation.implicitly implicitly]]` combinator. That combinator will only
 * wait for final results and ignore intermediate data.
 *
 * @see [[System#invoke]]
 */
abstract class Operation[I, O](val name: String, val toProver: Codec[I]) {
  def observer(env: Environment): env.Observer[O]

  /**
   * Convenience function which encodes an input argument via the
   * [[Codec codec]] for type `I`.
   */
  final def encode(env: Environment)(i: I): env.XMLTree = toProver.encode(env)(i)
}
