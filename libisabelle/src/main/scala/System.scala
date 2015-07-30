package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.Exception._
import scala.util.control.NoStackTrace

import edu.tum.cs.isabelle.api._

object System {

  case class CancellationException private[isabelle]() extends RuntimeException("Execution has been cancelled") with NoStackTrace

  def build(env: Environment)(config: env.Configuration): Boolean =
    env.build(config) == 0

  def createWithDefaultContext(env: Environment)(config: env.Configuration): Future[System] =
    create(env)(config)(env.executionContext)

  def create(env: Environment)(config: env.Configuration)(implicit ec: ExecutionContext): Future[System] = {
    class Output(name: String) {
      def unapply(markup: Markup): Option[Long] = markup match {
        case (env.protocolMarkup, (env.functionMarkup, `name`) :: ("id", id) :: Nil) =>
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
      val observer: env.Observer[T]
      val promise: Promise[ProverResult[T]]

      def withFirehose(firehose0: Boolean) = new OperationState {
        type T = self.T
        val firehose = firehose0
        val observer = self.observer
        val promise = self.promise
      }

      def withObserver(observer0: env.Observer[T]) = new OperationState {
        type T = self.T
        val firehose = self.firehose
        val observer = observer0
        val promise = self.promise
      }

      def tryComplete() = observer match {
        case env.Observer.Success(t) => promise.success(t); true
        case env.Observer.Failure(err) => promise.failure(err); true
        case _  => false
      }

      def advance(id: Long, markup: Markup, body: env.XMLBody) = observer match {
        case env.Observer.More(step, finish) =>
          (markup, body) match {
            case (Response(id1), List(tree)) if id == id1 =>
              withObserver(finish(tree))
            case (Start(id1), _) if id == id1 && !firehose =>
              withFirehose(true)
            case (Stop(id1), _) if id == id1 && firehose =>
              withFirehose(false)
            case _ if firehose =>
              withObserver(step(env.elem(markup, body)))
            case _ =>
              this
          }
        case _ =>
          this
      }
    }

    def makeOperationState[T0](observer0: env.Observer[T0]) = {
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
      val executionContext = ec

      private def consumer(markup: Markup, body: env.XMLBody): Unit = (markup, body) match {
        case ((env.initMarkup, _), _) => initPromise.success(()); ()
        case ((env.exitMarkup, _), _) => exitPromise.success(()); ()
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

      def cancelAll() = {
        val pending0 = self.synchronized {
          val pending0 = pending
          pending = Map.empty
          pending0
        }
        val ids = pending0.keys.toList.map(_.toString)
        env.sendCommand(session, "libisabelle_cancel", ids)
        pending0.values.foreach(_.promise.tryFailure(CancellationException()))
      }

      def invoke[I, O](operation: Operation[I, O])(arg: I) = {
        val state = makeOperationState(operation.observer(env))
        val count0 = self.synchronized {
          val count0 = count
          pending += (count -> state)
          count += 1
          count0
        }

        val encoded = operation.encode(env)(arg)
        val args = List(count0.toString, operation.name, env.toYXML(encoded))
        env.sendCommand(session, "libisabelle", args)
        state.promise.future
      }
    }.promise.future
  }

}

sealed abstract class System {
  private[isabelle] val promise: Promise[System]
  implicit val executionContext: ExecutionContext

  def dispose: Future[Unit]
  def cancelAll(): Unit
  def invoke[I, O](operation: Operation[I, O])(arg: I): Future[ProverResult[O]]
}
