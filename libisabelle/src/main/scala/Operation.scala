package edu.tum.cs.isabelle

import scala.util.control.NoStackTrace

import edu.tum.cs.isabelle.api.XML

import acyclic.file

/** Combinators for creating [[Operation operations]] and basic operations. */
object Operation {

  /**
   * Slightly fishy exception to represent any kind of exception from the
   * prover.
   *
   * There is no stack traces available, because instance are only created when
   * the prover throws an exception.
   */
  case class ProverException private[isabelle](operation: String, msg: String, input: Any) extends RuntimeException(msg) with NoStackTrace {
    def fullMessage =
      s"Prover error in operation $operation: $msg\nOffending input: $input"
  }

  /** Create a [[simple]] observer using implicit [[Codec codecs]]. */
  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    simple(name, Codec[I], Codec[O])

  /**
   * Creates an observer which ignores any intermediate data, waits for the
   * final result and immediately attempts to decode it with a given
   * [[Codec codec]].
   */
  def simple[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]): Operation[I, O] = {
    def exn(input: I) = Codec.text[Exception](
      _.getMessage,
      str => Some(ProverException(name, str, input))
    ).tagged("exn")

    def proverResult(input: I) = new Codec.Variant[ProverResult[O]]("Exn.result") {
      def enc(a: ProverResult[O]) = sys.error("impossible")
      def dec(idx: Int) = idx match {
        case 0 => Some(fromProver.decode(_).right.map(ProverResult.Success.apply))
        case 1 => Some(exn(input).decode(_).right.map(ProverResult.Failure.apply))
        case _ => None
      }
    }

    new Operation[I, O](name) {
      def prepare(i: I): (XML.Tree, Observer[O]) = {
        val tree = toProver.encode(i)
        val observer = Observer.ignoreStep[O] { tree =>
          proverResult(i).decode(tree) match {
            case Left((err, body)) => Observer.Failure(DecodingException(err, body))
            case Right(o) => Observer.Success(o)
          }
        }

        (tree, observer)
      }
    }
  }

  /** A simple ping operation. */
  val Hello = implicitly[String, String]("hello")

  /**
   * Load a list of theory files in the prover, specified by their paths.
   *
   * The format of the paths is rather peculiar: They are relative to the
   * working directory at the time the [[System.create system was created]],
   * but do not include the extension `.thy`. Theories which are already part
   * of the [[edu.tum.cs.isabelle.api.Configuration configuration]] should not
   * be loaded again.
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
 * [[edu.tum.cs.isabelle.api.XML.Tree XML trees]]. To convert between typed
 * data and their XML representation, [[Codec codecs]] may be used.
 *
 * In the most general case, an operation listens for a stream of output from
 * the prover using an [[edu.tum.cs.isabelle.Observer observer]], comparable to
 * iteratees.
 *
 * Operations can most easily be constructed with the
 * `[[Operation.implicitly implicitly]]` combinator. That combinator will only
 * wait for final results and ignore intermediate data.
 *
 * @see [[System#invoke]]
 */
abstract class Operation[I, O](val name: String) {

  /**
   * Prepare an input/output operation: Convert the input argument into an
   * [[edu.tum.cs.isabelle.api.XML.Tree XML tree]] and create a fresh
   * [[edu.tum.cs.isabelle.Observer observer]] to listen for results.
   */
  def prepare(i: I): (XML.Tree, Observer[O])

}
