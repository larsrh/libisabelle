package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.control.Exception._

import edu.tum.cs.isabelle.api._

object System {

  def build(env: Environment)(config: env.Configuration): Boolean =
    env.build(config) == 0

  def create(env: Environment)(config: env.Configuration)(implicit ec: ExecutionContext): System = {
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
      def consumer(markup: Markup, body: env.XMLBody): Unit = (markup, body) match {
        case ((env.initMarkup, _), _) => initPromise.success(()); ()
        case ((env.exitMarkup, _), _) => exitPromise.success(()); ()
        case _ =>
          self.synchronized {
            pending =
              pending.map { case (id, state) => id -> state.advance(id, markup, body) }
                     .filterNot(_._2.tryComplete())
          }
      }

      @volatile private var count = 0L
      @volatile private var pending = Map.empty[Long, OperationState]

      val session = env.create(config, consumer)
      initPromise.future foreach { _ =>
        env.sendOptions(session)
      }

      def dispose = {
        env.dispose(session)
        exitPromise.future
      }

      def invoke[I, O](operation: Operation[I, O])(arg: I) = {
        val args0 = List(count.toString, operation.name, env.toYXML(operation.encode(env)(arg)))
        val state = makeOperationState(operation.observer(env))
        self.synchronized {
          pending += (count -> state)
          count += 1
        }
        env.sendCommand(session, "libisabelle", args0)
        state.promise.future
      }
    }
  }

}

sealed abstract class System {
  def dispose: Future[Unit]
  def invoke[I, O](operation: Operation[I, O])(arg: I): Future[ProverResult[O]]
}
