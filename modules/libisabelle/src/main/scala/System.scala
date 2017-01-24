package info.hupel.isabelle

import scala.concurrent._
import scala.concurrent.duration._
import scala.util.control.Exception._
import scala.util.control.NoStackTrace

import cats.arrow.FunctionK
import cats.data.EitherT
import cats.instances.future._

import monix.execution.{Cancelable, CancelableFuture, FutureUtils, Scheduler}

import org.log4s._

import info.hupel.isabelle.api._

/** Functions to build and create [[System systems]]. */
object System {

  private val logger = getLogger

  object StartupException {
    sealed abstract class Reason(val explain: String)
    case object Exited extends Reason("exited (session not built?)")
    case object NoPong extends Reason("ping operation timeout (wrong session?)")
  }
  case class StartupException(reason: StartupException.Reason) extends RuntimeException(s"System startup failed: ${reason.explain}")

  /**
   * Synchronously build a
   * [[info.hupel.isabelle.api.Configuration configuration]].
   *
   * This operation is idempotent, but not parallel-safe. It must not be running
   * simultaneously for the same [[info.hupel.isabelle.setup.Setup setup]], not
   * even on different JVMs or with differnet configurations. Parallel
   * invocations of `[[create]]` should be avoided, but are safe under the
   * condition that they are using independent configurations, or that the
   * common ancestors of the configurations have already been successfully
   * built. Refer to the
   * [[info.hupel.isabelle.api.Configuration documentation of configurations]]
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

  private final case class OperationState[T](
    env: Environment,
    observer: Observer[T],
    firehose: Boolean = false,
    promise: Promise[ProverResult[T]] = Promise[ProverResult[T]]
  ) { self =>
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

    def tryComplete() = observer match {
      case Observer.Success(t) => promise.success(t); true
      case Observer.Failure(err) => promise.failure(err); true
      case _  => false
    }

    def advance(id: Long, markup: Markup, body: XML.Body) = observer match {
      case Observer.More(step, finish) =>
        (markup, body) match {
          case (Response(id1), List(tree)) if id == id1 =>
            copy(observer = finish(tree))
          case (Start(id1), _) if id == id1 && !firehose =>
            copy(firehose = true)
          case (Stop(id1), _) if id == id1 && firehose =>
            copy(firehose = false)
          case _ if firehose =>
            copy(observer = step(XML.elem(markup, body)))
          case _ =>
            this
        }
      case _ =>
        this
    }
  }

  /**
   * Asynchronously create a new [[System system]] based on the specified
   * [[info.hupel.isabelle.api.Configuration configuration]].
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
  def create(env: Environment, config: Configuration, pingTimeout: FiniteDuration = 5.seconds): Future[System] = {
    val Ping = Operation.implicitly[Unit, Unit]("ping")
    import env.scheduler

    val system = new System(env, config)
    system.initPromise.future.flatMap { _ =>
      logger.debug("Pinging system ...")
      val pong = system.invoke(Ping)(())
      pong.foreach { _ =>
        logger.debug("Ping operation successful")
      }
      FutureUtils.timeoutTo(
        pong,
        pingTimeout,
        Future.failed(StartupException(StartupException.NoPong))
      )
    }.map(_ => system)
  }

}

/**
 * A running instance of a prover.
 *
 * This class is thread-safe, that is, running multiple
 * [[Operation operations]] at the same time is expected and safe.
 *
 * @see [[info.hupel.isabelle.setup.Setup]]
 */
final class System private(val env: Environment, config: Configuration) {

  private val logger = getLogger

  /**
   * The [[scala.concurrent.ExecutionContext execution context]] used
   * internally for bi-directional communication with the prover.
   *
   * Guaranteed to be the same execution context as the
   * [[info.hupel.isabelle.api.Environment#executionContext execution context]]
   * of the [[info.hupel.isabelle.api.Environment environment]] used to
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
  implicit val scheduler: Scheduler = env.scheduler


  private val initPromise = Promise[Unit]
  private val exitPromise = Promise[Unit]

  @volatile private var count = 0L
  @volatile private var pending = Map.empty[Long, System.OperationState[_]]

  private def consumer(markup: Markup, body: XML.Body): Unit = (markup, body) match {
    case ((env.initTag, _), _) =>
      env.sendOptions(session)
      logger.debug("Session started")
      initPromise.success(())
      ()
    case ((env.exitTag, _), _) =>
      initPromise.tryFailure(System.StartupException(System.StartupException.Exited))
      logger.debug("Session terminated")
      exitPromise.success(())
      ()
    case ((tag, _), body) if env.printTags contains tag =>
      body.foreach { tree =>
        logger.trace(s"Output ($tag): ${tree.stripMarkup}")
      }
    case _ =>
      synchronized {
        pending =
          pending.map { case (id, state) => id -> state.advance(id, markup, body) }
                 .filterNot(_._2.tryComplete())
                 .toMap
      }
  }

  logger.debug("Starting session ...")
  private val session = env.create(config, consumer)

  /**
   * Instruct the prover to shutdown.
   *
   * Includes unchecked cancellation of all running operations by virtue of
   * the system shutting down completely. Pending futures will not be marked as
   * failed.
   *
   * It is recommended to wait for all pending futures to complete, or call
   * `cancel` on them before shutdown. It is guaranteed that when the returned
   * [[scala.concurrent.Future future]] succeeds, the prover has been shut
   * down.
   *
   * Calling anything after dispose is undefined. The object should not be used
   * afterwards.
   *
   * Depending on the
   * [[info.hupel.isabelle.api.Environment#executionContext implementation details]]
   * of the [[info.hupel.isabelle.api.Environment environment]] used to
   * [[System.create create]] this system, it may be unneccessary to call this
   * method. In any case, it is good practice to call it.
   *
   * @see [[executionContext]]
   */
  def dispose: Future[Unit] = {
    env.dispose(session)
    exitPromise.future
  }

  /**
   * Invoke an [[Operation operation]] on the prover, that is,
   * [[Codec#encode encode]] the input argument, send it to the prover and
   * stream the results to the [[Observer observer]] of the operation.
   *
   * The returned [[scala.concurrent.Future future]] gets fulfilled when the
   * observer transitions into either
   * `[[info.hupel.isabelle.Observer.Success Success]]` or
   * `[[info.hupel.isabelle.Observer.Failure Failure]]` state.
   *
   * In addition to that, the transaction can be cancelled via the `cancel`
   * method of the `CancelableFuture`. Cancellation entails marking the future
   * as failed and signalling the prover that the operation should be
   * interrupted.
   *
   * Any well-formed response, even if it is an "error", is treated as a
   * success. Only ill-formed responses, e.g. due to [[Codec#decode decoding]]
   * errors, will mark the future as failed. Custom observers may deviate from
   * this, but it is generally safe to assume that a failed future represents
   * an internal error (e.g. due to a wrong [[Codec codec]]), whereas a
   * successful future may contain expected errors (e.g. due to a wrong input
   * argument or a failing proof).
   */
  def invoke[I, O](operation: Operation[I, O])(arg: I): CancelableFuture[ProverResult[O]] = {
    val (encoded, observer) = operation.prepare(arg)
    val state = new System.OperationState(env, observer)
    state.tryComplete()
    val count0 = synchronized {
      val count0 = count
      pending += (count -> state)
      count += 1
      count0
    }

    val args = List(count0.toString, operation.name, encoded.toYXML)
    env.sendCommand(session, "libisabelle", args)
    val promise = state.promise
    val cancel = Cancelable { () =>
      promise.tryFailure(new CancellationException())
      env.sendCommand(session, "libisabelle_cancel", List(count0.toString))
    }
    CancelableFuture(promise.future, cancel)
  }

  def run[A](prog: Program[A], thyName: String): Future[A] = {
    val interpreter = new FunctionK[Instruction, Future] {
      def apply[T](instruction: Instruction[T]) =
        instruction.run(System.this, thyName).map(_.unsafeGet)
    }
    prog.foldMap(interpreter)
  }

}
