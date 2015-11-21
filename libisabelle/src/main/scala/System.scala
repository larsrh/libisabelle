package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.Exception._
import scala.util.control.NoStackTrace

import edu.tum.cs.isabelle.api._

import acyclic.file

/** Functions to build and create [[System systems]]. */
object System {

  /**
   * Synchronously build a
   * [[edu.tum.cs.isabelle.api.Configuration configuration]].
   *
   * This operation is idempotent, but not parallel-safe. It must not be running
   * simultaneously for the same [[edu.tum.cs.isabelle.setup.Setup setup]], not
   * even on different JVMs or with differnet configurations. Parallel
   * invocations of `[[create]]` should be avoided, but are safe under the
   * condition that they are using independent configurations, or that the
   * common ancestors of the configurations have already been successfully
   * built. Refer to the
   * [[edu.tum.cs.isabelle.api.Configuration documentation of configurations]]
   * for more details.
   *
   * A `true` return value indicates a successful build. Currently, there is no
   * way to elaborate on a failed build.
   *
   * In the background, this will spawn a prover instance running in a special,
   * non-interactive mode. Build products will be put into `~/.isabelle` on the
   * file system.
   */
  def build(env: Environment, config: Configuration): Boolean =
    env.build(config) == 0

  /**
   * Asynchronously create a new [[System system]] based on the specified
   * [[edu.tum.cs.isabelle.api.Configuration configuration]].
   *
   * The behaviour of this function when the given configuration has not been
   * [[build built]] yet is unspecified. Since building is idempotent, it is
   * recommended to always build a configuration at least once before creating
   * a system.
   *
   * This function is thread-safe. It is safe to create multiple system
   * based on the same environment or even configuration. It is guaranteed that
   * they are independent, that is, calling any method will not influence other
   * systems.
   *
   * Build products will be read from `~/.isabelle` on the file system.
   */
  def create(env: Environment, config: Configuration): Future[System] = {
    class Output(name: String) {
      def unapply(markup: Markup): Option[Long] = markup match {
        case (env.protocolTag, (env.functionTag, `name`) :: ("id", id) :: Nil) =>
          catching(classOf[NumberFormatException]) opt id.toLong
        case _ =>
          None
      }
    }

    object Response extends Output("libisabelle_response")
    object Start extends Output("libisabelle_start")
    object Stop extends Output("libisabelle_stop")

    trait OperationState { self =>
      type T
      val firehose: Boolean
      val observer: Observer[T]
      val promise: Promise[ProverResult[T]]

      def withFirehose(firehose0: Boolean) = new OperationState {
        type T = self.T
        val firehose = firehose0
        val observer = self.observer
        val promise = self.promise
      }

      def withObserver(observer0: Observer[T]) = new OperationState {
        type T = self.T
        val firehose = self.firehose
        val observer = observer0
        val promise = self.promise
      }

      def tryComplete() = observer match {
        case Observer.Success(t) => promise.success(t); true
        case Observer.Failure(err) => promise.failure(err); true
        case _  => false
      }

      def advance(id: Long, markup: Markup, body: XML.Body) = observer match {
        case Observer.More(step, finish) =>
          (markup, body) match {
            case (Response(id1), List(tree)) if id == id1 =>
              withObserver(finish(tree))
            case (Start(id1), _) if id == id1 && !firehose =>
              withFirehose(true)
            case (Stop(id1), _) if id == id1 && firehose =>
              withFirehose(false)
            case _ if firehose =>
              withObserver(step(XML.elem(markup, body)))
            case _ =>
              this
          }
        case _ =>
          this
      }
    }

    def makeOperationState[T0](observer0: Observer[T0]) = {
      val state = new OperationState {
        type T = T0
        val firehose = false
        val observer = observer0
        val promise = Promise[ProverResult[T]]
      }
      state.tryComplete()
      state
    }

    val initPromise = Promise[Unit]
    val exitPromise = Promise[Unit]

    new System { self =>
      implicit val executionContext = env.executionContext

      private def consumer(markup: Markup, body: XML.Body): Unit = (markup, body) match {
        case ((env.initTag, _), _) => initPromise.success(()); ()
        case ((env.exitTag, _), _) => exitPromise.success(()); ()
        case _ =>
          self.synchronized {
            pending =
              pending.map { case (id, state) => id -> state.advance(id, markup, body) }
                     .filterNot(_._2.tryComplete())
          }
      }

      private[isabelle] val promise = Promise[System]

      @volatile private var count = 0L
      @volatile private var pending = Map.empty[Long, OperationState]

      private val session = env.create(config, consumer)
      initPromise.future foreach { _ =>
        env.sendOptions(session)
        promise.success(self)
      }

      def dispose = {
        env.dispose(session)
        exitPromise.future
      }

      def cancel(id: Long) =
        env.sendCommand(session, "libisabelle_cancel", List(id.toString))

      def cancellableInvoke[I, O](operation: Operation[I, O])(arg: I) = {
        val (encoded, observer) = operation.prepare(arg)
        val state = makeOperationState(observer)
        val count0 = self.synchronized {
          val count0 = count
          pending += (count -> state)
          count += 1
          count0
        }

        val args = List(count0.toString, operation.name, encoded.toYXML)
        env.sendCommand(session, "libisabelle", args)
        new CancellableFuture(state.promise, () => cancel(count0))
      }
    }.promise.future
  }

}

/**
 * A running instance of a prover.
 *
 * This class is thread-safe, that is, running multiple
 * [[Operation operations]] at the same time is expected and safe.
 *
 * @see [[edu.tum.cs.isabelle.setup.Setup]]
 */
sealed abstract class System {

  private[isabelle] val promise: Promise[System]

  /**
   * The [[scala.concurrent.ExecutionContext execution context]] used
   * internally for bi-directional communication with the prover.
   *
   * Guaranteed to be the same execution context as the
   * [[edu.tum.cs.isabelle.api.Environment#executionContext execution context]]
   * of the [[edu.tum.cs.isabelle.api.Environment environment]] used to
   * [[System.create create]] this system.
   *
   * It is fine to use this execution context for other purposes, for example
   * to transform the [[scala.concurrent.Future futures]] produced by
   * [[invoke invoking operations]].
   *
   * Since it is marked as `implicit`, it can be readily imported and used.
   *
   * @see [[dispose]]
   */
  implicit val executionContext: ExecutionContext

  /**
   * Instruct the prover to shutdown.
   *
   * Includes unchecked cancellation of all running operations by virtue of
   * the system shutting down completely. Pending futures will not be marked as
   * failed.
   *
   * It is recommended to wait for all pending futures to complete, or call
   * `[[CancellableFuture#cancel cancel]]` on them before shutdown. It is
   * guaranteed that when the returned [[scala.concurrent.Future future]]
   * succeeds, the prover has been shut down.
   *
   * Calling anything after dispose is undefined. The object should not be used
   * afterwards.
   *
   * Depending on the
   * [[edu.tum.cs.isabelle.api.Environment#executionContext implementation details]]
   * of the [[edu.tum.cs.isabelle.api.Environment environment]] used to
   * [[System.create create]] this system, it may be unneccessary to call this
   * method. In any case, it is good practice to call it.
   *
   * @see [[executionContext]]
   */
  def dispose: Future[Unit]

  /**
   * Invoke an [[Operation operation]] on the prover, that is,
   * [[Codec#encode encode]] the input argument, send it to the prover and
   * stream the results to the [[Observer observer]] of the operation.
   *
   * The observer is automatically [[Operation#prepare instantiated]] with
   * the underlying [[edu.tum.cs.isabelle.api.Environment environment]]
   * specified when [[System#create creating]] the system.
   *
   * The returned [[scala.concurrent.Future future]] gets fulfilled when the
   * observer transitions into either
   * `[[edu.tum.cs.isabelle.Observer.Success Success]]` or
   * `[[edu.tum.cs.isabelle.Observer.Failure Failure]]` state.
   *
   * Any well-formed response, even if it is an "error", is treated as a
   * success. Only ill-formed responses, e.g. due to [[Codec#decode decoding]]
   * errors, will mark the future as failed. Custom observers may deviate from
   * this, but it is generally safe to assume that a failed future represents
   * an internal error (e.g. due to a wrong [[Codec codec]]), whereas a
   * successful future may contain expected errors (e.g. due to a wrong input
   * argument or a failing proof).
   */
  def cancellableInvoke[I, O](operation: Operation[I, O])(arg: I): CancellableFuture[ProverResult[O]]

  final def invoke[I, O](operation: Operation[I, O])(arg: I): Future[ProverResult[O]] =
    cancellableInvoke(operation)(arg).future

}
