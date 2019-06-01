/*  Title:      Pure/Concurrent/standard_thread.scala
    Author:     Makarius

Standard thread operations.
*/

package isabelle


import java.lang.Thread
import java.util.concurrent.{ExecutorService, ThreadPoolExecutor, TimeUnit, LinkedBlockingQueue, ThreadFactory}


object Standard_Thread
{
  /* fork */

  def fork(name: String = "", daemon: Boolean = false)(body: => Unit): Thread =
  {
    val thread =
      if (name == null || name == "") new Thread() { override def run = body }
      else new Thread(name) { override def run = body }
    thread.setDaemon(daemon)
    thread.start
    thread
  }


  /* pool */

  var pool: ExecutorService = null


  /* delayed events */

  final class Delay private[Standard_Thread](
    first: Boolean, delay: => Time, log: Logger, event: => Unit)
  {
    private var running: Option[Event_Timer.Request] = None

    private def run: Unit =
    {
      val do_run = synchronized {
        if (running.isDefined) { running = None; true } else false
      }
      if (do_run) {
        try { event }
        catch { case exn: Throwable if !Exn.is_interrupt(exn) => log(Exn.message(exn)); throw exn }
      }
    }

    def invoke(): Unit = synchronized
    {
      val new_run =
        running match {
          case Some(request) => if (first) false else { request.cancel; true }
          case None => true
        }
      if (new_run)
        running = Some(Event_Timer.request(Time.now() + delay)(run))
    }

    def revoke(): Unit = synchronized
    {
      running match {
        case Some(request) => request.cancel; running = None
        case None =>
      }
    }

    def postpone(alt_delay: Time): Unit = synchronized
    {
      running match {
        case Some(request) =>
          val alt_time = Time.now() + alt_delay
          if (request.time < alt_time && request.cancel) {
            running = Some(Event_Timer.request(alt_time)(run))
          }
        case None =>
      }
    }
  }

  // delayed event after first invocation
  def delay_first(delay: => Time, log: Logger = No_Logger)(event: => Unit): Delay =
    new Delay(true, delay, log, event)

  // delayed event after last invocation
  def delay_last(delay: => Time, log: Logger = No_Logger)(event: => Unit): Delay =
    new Delay(false, delay, log, event)
}
