package edu.tum.cs.isabelle

import scala.concurrent.{ExecutionContext, Future, Promise}
import scala.util.{Failure, Success, Try}

import isabelle._

object System {

  // public interface

  case class ProverException(msg: String) extends RuntimeException(msg)

  def instance(sessionPath: Option[java.io.File], sessionName: String)(implicit ec: ExecutionContext): Future[System] = synchronized {
    val path = sessionPath.map(f => Path.explode(f.getAbsolutePath()))
    val session = startSession(path, sessionName)

    val id = count
    val system = session.map(new System(id, Options.init(), _))
    instances += (id -> ((ec, system)))
    count += 1
    system
  }


  // implementation details

  @volatile private var count = 0L
  @volatile private var instances = Map.empty[Long, (ExecutionContext, Future[System])]

  class Handler extends Session.Protocol_Handler { // public, because PIDE is going to reflectively instantiate this

    private val SYS_ID = "sys_id"
    private val REQ_ID = "req_id"
    private val LIBISABELLE_RESPONSE = "libisabelle_response"

    private object Libisabelle_Response {
      def unapply(props: Properties.T): Option[(Long, Long)] = props match {
        case List(
          (Markup.FUNCTION, LIBISABELLE_RESPONSE),
          (SYS_ID, Properties.Value.Long(sysID)),
          (REQ_ID, Properties.Value.Long(reqID))) => Some((sysID, reqID))
        case _ => None
      }
    }

    private def response(prover: Prover, msg: Prover.Protocol_Output): Boolean = synchronized {
      def fulfill(sysID: Long, reqID: Long) = {
        implicit val (ec, instance) = instances(sysID)
        instance foreach { instance =>
          instance.synchronized {
            instance.pending(reqID).success(msg)
            instance.pending -= reqID
          }
        }
      }

      msg.properties match {
        case Libisabelle_Response(sysID, reqID) => fulfill(sysID, reqID); true
        case _ => false
      }
    }

    // FIXME make name configurable
    val functions = Map(LIBISABELLE_RESPONSE -> response _)

  }


  private def mkListener[T, U](outlet: Session.Outlet[T], name: String)(f: T => Option[U])(implicit ec: ExecutionContext): Future[U] = {
    val promise = Promise[U]
    val consumer = Session.Consumer[T](name) { msg =>
      f(msg).foreach(u => promise.trySuccess(u))
    }
    outlet += consumer

    val future = promise.future
    future foreach { _ => outlet -= consumer }
    future
  }

  private def mkPhaseListener(session: Session, phase: Session.Phase)(implicit ec: ExecutionContext): Future[Unit] =
    mkListener(session.phase_changed, "phase-listener") {
      case `phase` => Some(())
      case _ => None
    }

  private def mkUpdateListener(session: Session)(implicit ec: ExecutionContext): Future[Prover.Protocol_Output] =
    mkListener(session.all_messages, "update-listener") {
      case msg: Prover.Protocol_Output =>
        msg.properties match {
          case Markup.Assign_Update => Some(msg)
          case _ => None
        }
      case _ => None
    }

  private def startSession(path: Option[Path], sessionName: String)(implicit ec: ExecutionContext): Future[Session] = {
    val options = Options.init()

    val content = Build.session_content(options, false, path.toList, sessionName)
    val resources = new Resources(content.loaded_theories, content.known_theories, content.syntax)

    val session = new Session(resources)

    val master =
      mkPhaseListener(session, Session.Ready) flatMap { _ =>
        val updated = mkUpdateListener(session)
        session.update_options(options)
        updated
      } map { _ =>
        session
      }

    session.start("Isabelle" /* name is ignored anyway */, List("-r", "-q", sessionName))
    master
  }

}

class System private(id: Long, options: Options, session: Session)(implicit ec: ExecutionContext) {

  @volatile private var count = 0L
  @volatile private var pending = Map.empty[Long, Promise[Prover.Protocol_Output]]

  def dispose(): Future[Unit] = {
    // FIXME kill pending executions
    val future = System.mkPhaseListener(session, Session.Inactive)
    future foreach { _ =>
      System.synchronized { System.instances -= id }
    }
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


  private val decodeCommand: XML.Decode.T[Try[XML.Body]] =
    XML.Decode.variant(List(
      { case (List(), a) => Success(a) },
      { case (List(), exn) => Failure(System.ProverException(XML.content(exn))) }
    ))

  def sendCommand(command: String, args: XML.Body*): Future[XML.Body] =
    withRequest {
      val args0 = List(id.toString, count.toString, command) ::: args.toList.map(YXML.string_of_body)
      session.protocol_command("libisabelle", args0: _*)
    } flatMap { msg =>
      Future.fromTry(decodeCommand(YXML.parse_body(msg.text)))
    }

}
