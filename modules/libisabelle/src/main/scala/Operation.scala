package info.hupel.isabelle

import info.hupel.isabelle.api.XML

/** Combinators for creating [[Operation operations]] and basic operations. */
object Operation {

  /** Create a [[simple]] observer using implicit [[Codec codecs]]. */
  def implicitly[I : Codec, O : Codec](name: String): Operation[I, O] =
    simple(name, Codec[I], Codec[O])

  /**
   * Creates an observer which ignores any intermediate data, waits for the
   * final result and immediately attempts to decode it with a given
   * [[Codec codec]].
   */
  def simple[I, O](name: String, toProver: Codec[I], fromProver: Codec[O]): Operation[I, O] = new Operation[I, O](name) {
    def prepare(i: I): (XML.Tree, Observer[O]) = {
      val tree = toProver.encode(i)
      val observer = Observer.ignoreStep[O] { tree =>
        ProverResult.resultCodec(fromProver, name, i).decode(tree) match {
          case Left((err, body)) => Observer.Failure(DecodingException(err, body))
          case Right(o) => Observer.Success(o)
        }
      }

      (tree, observer)
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
   * of the [[info.hupel.isabelle.api.Configuration configuration]] should not
   * be loaded again.
   */
  def UseThys[A, B](init: A)(markup: (A, XML.Tree) => A, finish: A => B): Operation[List[String], B] = new Operation[List[String], B]("use_thys") {
    def prepare(args: List[String]): (XML.Tree, Observer[B]) = {
      val tree = Codec[List[String]].encode(args)
      def observer(a: A): Observer[B] = Observer.More(
        step = msg => observer(markup(a, msg)),
        done = { tree =>
          ProverResult.resultCodec[Unit](Codec[Unit], name, args).decode(tree) match {
            case Left((err, body)) => Observer.Failure(DecodingException(err, body))
            case Right(ProverResult.Success(())) => Observer.Success(ProverResult.Success(finish(a)))
            case Right(fail @ ProverResult.Failure(_)) => Observer.Success(fail)
          }
        }
      )

      (tree, observer(init))
    }
  }

  def UseThys: Operation[List[String], Unit] = UseThys(())((_, _) => (), _ => ())

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
 * [[info.hupel.isabelle.api.XML.Tree XML trees]]. To convert between typed
 * data and their XML representation, [[Codec codecs]] may be used.
 *
 * In the most general case, an operation listens for a stream of output from
 * the prover using an [[info.hupel.isabelle.Observer observer]], comparable to
 * iteratees.
 *
 * Operations can most easily be constructed with the
 * `[[Operation.implicitly implicitly]]` combinator. That combinator will only
 * wait for final results and ignore intermediate data.
 *
 * @see [[System#invoke]]
 */
abstract class Operation[-I, +O](val name: String) { self =>

  /**
   * Prepare an input/output operation: Convert the input argument into an
   * [[info.hupel.isabelle.api.XML.Tree XML tree]] and create a fresh
   * [[info.hupel.isabelle.Observer observer]] to listen for results.
   */
  def prepare(i: I): (XML.Tree, Observer[O])

  def map[J, P](f: J => I, g: O => P): Operation[J, P] = new Operation[J, P](name) {
    def prepare(j: J) = {
      val (tree, observer) = self.prepare(f(j))
      (tree, observer map g)
    }
  }

}
