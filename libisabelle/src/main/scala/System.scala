package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

import isabelle._

object System {

  def instance(sessionPath: Option[java.io.File], sessionName: String)(implicit ec: ExecutionContext): Future[System] = {
    val path = sessionPath.map(f => Path.explode(f.getAbsolutePath()))
    val session = startSession(path, sessionName)

    session.map(new System(Options.init(), _))
  }


  // implementation details

  private val ID = "id"
  private val LIBISABELLE_RESPONSE = "libisabelle_response"

  private object Libisabelle_Response {
    def unapply(props: Properties.T): Option[Long] = props match {
      case
        (Markup.FUNCTION, LIBISABELLE_RESPONSE) ::
        (ID, Properties.Value.Long(id)) :: Nil => Some(id)
      case _ => None
    }
  }

  private def mkPhaseListener(session: Session, phase: Session.Phase)(implicit ec: ExecutionContext): Future[Unit] = {
    val promise = Promise[Unit]
    val consumer = Session.Consumer[Session.Phase]("phase-listener") {
      case `phase` => promise.trySuccess(()); ()
      case _ =>
    }
    session.phase_changed += consumer
    val future = promise.future
    future foreach { _ => session.phase_changed -= consumer }
    future
  }

  private def sendOptions(session: Session, options: Options)(implicit ec: ExecutionContext): Future[Unit] =
    Future {
      session.protocol_command("Prover.options", YXML.string_of_body(options.encode))
    }

  private def startSession(path: Option[Path], sessionName: String)(implicit ec: ExecutionContext): Future[Session] = {
    val options = Options.init()

    val content = Build.session_content(options, false, path.toList, sessionName)
    val resources = new Resources(content.loaded_theories, content.known_theories, content.syntax)

    val session = new Session(resources)

    val master =
      for {
        () <- mkPhaseListener(session, Session.Ready)
        () <- sendOptions(session, options)
      } yield session

    session.start("Isabelle" /* name is ignored anyway */, List("-r", "-q", sessionName))
    master
  }


  private trait OperationState { self =>
    type T
    val id: Long
    val bracketed: Boolean
    val observer: Observer[T]
    val promise: Promise[Exn.Result[T]]

    def withObserver(observer0: Observer[T]) = new OperationState {
      type T = self.T
      val id = self.id
      val bracketed = self.bracketed
      val observer = observer0
      val promise = self.promise
    }

    def tryComplete() = observer match {
      case Observer.Success(t) => promise.success(t); true
      case Observer.Failure(err) => promise.failure(err); true
      case _  => false
    }

    def advance(msg: Prover.Message) = observer match {
      case Observer.More(step, finish) => msg match {
        case msg: Prover.Protocol_Output =>
          msg.properties match {
            case System.Libisabelle_Response(id) if id == this.id =>
              val xml = YXML.parse(msg.text)
              withObserver(finish(xml))
            case _ =>
              this
          }
        case _ =>
          this
      }
      case _ =>
        this
    }

  }

  private def mkOperationState[T0](id0: Long, observer0: Observer[T0]) = {
    val state = new OperationState {
      type T = T0
      val id = id0
      val bracketed = false
      val observer = observer0
      val promise = Promise[Exn.Result[T]]
    }
    state.tryComplete()
    state
  }

}

class System private(options: Options, session: Session)(implicit ec: ExecutionContext) { self =>

  @volatile private var count = 0L
  @volatile private var pending = Map.empty[Long, System.OperationState]

  session.all_messages += Session.Consumer[Prover.Message]("firehose") {
    case msg =>
      self.synchronized {
        pending = pending.mapValues(_.advance(msg)).filterNot(_._2.tryComplete())
      }
  }

  def dispose: Future[Unit] = {
    // FIXME kill pending executions
    val future = System.mkPhaseListener(session, Session.Inactive)
    session.stop()
    future
  }

  def invoke[I, O](operation: Operation[I, O])(arg: I): Future[Exn.Result[O]] = {
    val args0 = List(count.toString, operation.name, YXML.string_of_tree(operation.encode(arg)))
    val promise = synchronized {
      val state = System.mkOperationState(count, operation.observer)
      pending += (count -> state)
      count += 1
      state.promise
    }
    session.protocol_command("libisabelle", args0: _*)
    promise.future
  }

}
