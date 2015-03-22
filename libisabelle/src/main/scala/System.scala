package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

import isabelle._

object System {

  // public interface

  case class ProverException(msg: String) extends RuntimeException(msg)

  def instance(sessionPath: Option[java.io.File], sessionName: String)(implicit ec: ExecutionContext): Future[System] = {
    val path = sessionPath.map(f => Path.explode(f.getAbsolutePath()))
    val session = startSession(path, sessionName)

    session.map(new System(Options.init(), _, path.getOrElse(Path.current)))
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
      case `phase` => promise.trySuccess(())
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

}

class System private(options: Options, session: Session, root: Path)(implicit ec: ExecutionContext) { self =>

  @volatile private var count = 0L
  @volatile private var pending = Map.empty[Long, Promise[Prover.Protocol_Output]]

  session.all_messages += Session.Consumer[Prover.Message]("firehose") {
    case msg: Prover.Protocol_Output =>
      (msg.properties match {
        case System.Libisabelle_Response(id) => Some(id)
        case _ => None
      }) foreach { id =>
        self.synchronized {
          pending(id).success(msg)
          pending -= id
        }
      }
    case _ =>
  }

  def dispose: Future[Unit] = {
    // FIXME kill pending executions
    val future = System.mkPhaseListener(session, Session.Inactive)
    session.stop()
    future
  }

  private def withRequest(f: => Unit): Future[Prover.Protocol_Output] = synchronized {
    val promise = Promise[Prover.Protocol_Output]
    pending += (count -> promise)
    f
    count += 1
    promise.future
  }


  private val decodeResponse: XML.Decode.T[Try[XML.Body]] =
    XML.Decode.variant(List(
      { case (List(), a) => Success(a) },
      { case (List(), exn) => Failure(System.ProverException(XML.content(exn))) }
    ))

  def invokeRaw(name: String, args: XML.Body*): Future[XML.Body] =
    withRequest {
      val args0 = List(count.toString, name) ::: args.toList.map(YXML.string_of_body)
      session.protocol_command("libisabelle", args0: _*)
    } flatMap { msg =>
      Future.fromTry(decodeResponse(YXML.parse_body(msg.text)))
    }

  def invoke[I, O](operation: Operation[I, O])(arg: I): Future[Either[XML.Error, O]] =
    invokeRaw(operation.name, operation.toProver(arg): _*).map(operation.fromProver)

}
