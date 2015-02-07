package edu.tum.cs.isabelle

import scala.concurrent.ExecutionContext.Implicits.global // FIXME make EC configurable
import scala.concurrent.{Future, Promise}
import scala.util.{Failure, Success, Try}

import isabelle._

object System {

  // public interface

  case class ProverException(msg: String) extends RuntimeException(msg)

  def instance(sessionPath: java.io.File, sessionName: String): Future[System] = synchronized {
    val session = startSession(sessionPath, sessionName)

    val id = count
    val system = session.map(new System(id, Options.init(), _))
    instances += (id -> system)
    count += 1
    system
  }


  // implementation details

  @volatile private var count = 0L
  @volatile private var instances = Map.empty[Long, Future[System]]

  class Handler extends Session.Protocol_Handler { // public, because PIDE is going to reflectively instantiate this

     private val SysID = new Properties.Long("sys_id")
     private val ReqID = new Properties.Long("req_id")

     private val decode: XML.Decode.T[Try[XML.Body]] =
       XML.Decode.variant(List(
         { case (List(), a) => Success(a) },
         { case (List(), exn) => Failure(ProverException(XML.content(exn))) }
       ))

     private def response(prover: Prover, msg: Prover.Protocol_Output): Boolean = synchronized {
       (msg.properties, msg.properties) match {
         case (SysID(sysID), ReqID(reqID)) =>
           instances(sysID) foreach { instance =>
             val decoded = decode(YXML.parse_body(msg.text))
             instance.synchronized {
               instance.pending(reqID).tryComplete(decoded)
               instance.pending -= reqID
             }
           }
           true
         case _ =>
           false
       }
     }

     val functions = Map("libisabelle_response" -> response _)

  }


  private def mkListener[T, U](outlet: Session.Outlet[T], name: String)(f: T => Option[U]): Future[U] = {
    val promise = Promise[U]
    val consumer = Session.Consumer[T](name) { msg =>
      f(msg).foreach(u => promise.tryComplete(Success(u)))
    }
    outlet += consumer

    val future = promise.future
    future foreach { _ => outlet -= consumer }
    future
  }

  private def mkPhaseListener(session: Session, phase: Session.Phase): Future[Unit] =
    mkListener(session.phase_changed, "phase-listener") {
      case `phase` => Some(())
      case _ => None
    }

  private def mkUpdateListener(session: Session): Future[Prover.Protocol_Output] =
    mkListener(session.all_messages, "update-listener") {
      case msg: Prover.Protocol_Output =>
        msg.properties match {
          case Markup.Assign_Update => Some(msg)
          case _ => None
        }
      case _ => None
    }

  private def startSession(sessionPath: java.io.File, sessionName: String): Future[Session] = {
    val dirs = List(Path.explode(sessionPath.getAbsolutePath()))

    val options = Options.init()

    val content = Build.session_content(options, false, dirs, sessionName)
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


class System private(id: Long, options: Options, session: Session) {

  @volatile private var count = 0L
  @volatile private var pending = Map.empty[Long, Promise[XML.Body]]

  def dispose(): Unit = {
    // FIXME kill pending executions
    System.synchronized { System.instances -= id }
  }

  private def sendCommand(command: String, args: XML.Body*): Future[XML.Body] = synchronized {
    val promise = Promise[XML.Body]
    pending += (count -> promise)

    val args0 = List(id.toString, count.toString, command) ::: args.toList.map(YXML.string_of_body)
    session.protocol_command("libisabelle", args0: _*)

    count += 1
    promise.future
  }

}
