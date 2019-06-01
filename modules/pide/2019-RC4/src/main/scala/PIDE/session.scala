/*  Title:      Pure/PIDE/session.scala
    Author:     Makarius
    Options:    :folding=explicit:

PIDE editor session, potentially with running prover process.
*/

package isabelle


import scala.collection.immutable.Queue
import scala.util.control.NonFatal
import scala.annotation.tailrec


object Session
{
  /* outlets */

  object Consumer
  {
    def apply[A](name: String)(consume: A => Unit): Consumer[A] =
      new Consumer[A](name, consume)
  }
  final class Consumer[-A] private(val name: String, val consume: A => Unit)

  class Outlet[A](dispatcher: Consumer_Thread[() => Unit])
  {
    private val consumers = Synchronized[List[Consumer[A]]](Nil)

    def += (c: Consumer[A]) { consumers.change(Library.update(c)) }
    def -= (c: Consumer[A]) { consumers.change(Library.remove(c)) }

    def post(a: A)
    {
      for (c <- consumers.value.iterator) {
        dispatcher.send(() =>
          try { c.consume(a) }
          catch {
            case exn: Throwable =>
              Output.error_message("Consumer failed: " + quote(c.name) + "\n" + Exn.message(exn))
          })
      }
    }
  }


  /* change */

  sealed case class Change(
    previous: Document.Version,
    syntax_changed: List[Document.Node.Name],
    deps_changed: Boolean,
    doc_edits: List[Document.Edit_Command],
    consolidate: List[Document.Node.Name],
    version: Document.Version)

  case object Change_Flush


  /* events */

  //{{{
  case class Statistics(props: Properties.T)
  case class Global_Options(options: Options)
  case object Caret_Focus
  case class Raw_Edits(doc_blobs: Document.Blobs, edits: List[Document.Edit_Text])
  case class Dialog_Result(id: Document_ID.Generic, serial: Long, result: String)
  case class Build_Theories(id: String, master_dir: Path, theories: List[(Options, List[Path])])
  case class Commands_Changed(
    assignment: Boolean, nodes: Set[Document.Node.Name], commands: Set[Command])

  sealed abstract class Phase
  {
    def print: String =
      this match {
        case Terminated(result) => if (result.ok) "finished" else "failed"
        case _ => Word.lowercase(this.toString)
      }
  }
  case object Inactive extends Phase  // stable
  case object Startup extends Phase  // transient
  case object Ready extends Phase  // metastable
  case object Shutdown extends Phase  // transient
  case class Terminated(result: Process_Result) extends Phase  // stable
  //}}}


  /* syslog */

  private[Session] class Syslog(limit: Int)
  {
    private var queue = Queue.empty[XML.Elem]
    private var length = 0

    def += (msg: XML.Elem): Unit = synchronized {
      queue = queue.enqueue(msg)
      length += 1
      if (length > limit) queue = queue.dequeue._2
    }

    def content: String = synchronized {
      cat_lines(queue.iterator.map(XML.content)) +
      (if (length > limit) "\n(A total of " + length + " messages...)" else "")
    }
  }


  /* protocol handlers */

  abstract class Protocol_Handler
  {
    def init(session: Session): Unit = {}
    def exit(): Unit = {}
    val functions: List[(String, Prover.Protocol_Output => Boolean)]
  }
}


class Session(_session_options: => Options, val resources: Resources) extends Document.Session
{
  session =>

  val xml_cache: XML.Cache = XML.make_cache()
  val xz_cache: XZ.Cache = XZ.make_cache()


  /* global flags */

  @volatile var timing: Boolean = false
  @volatile var verbose: Boolean = false


  /* dynamic session options */

  def session_options: Options = _session_options

  def output_delay: Time = session_options.seconds("editor_output_delay")
  def consolidate_delay: Time = session_options.seconds("editor_consolidate_delay")
  def prune_delay: Time = session_options.seconds("editor_prune_delay")
  def prune_size: Int = session_options.int("editor_prune_size")
  def syslog_limit: Int = session_options.int("editor_syslog_limit")
  def reparse_limit: Int = session_options.int("editor_reparse_limit")


  /* dispatcher */

  private val dispatcher =
    Consumer_Thread.fork[() => Unit]("Session.dispatcher", daemon = true) { case e => e(); true }

  def assert_dispatcher[A](body: => A): A =
  {
    assert(dispatcher.check_thread)
    body
  }

  def require_dispatcher[A](body: => A): A =
  {
    require(dispatcher.check_thread)
    body
  }

  def send_dispatcher(body: => Unit): Unit =
  {
    if (dispatcher.check_thread) body
    else dispatcher.send(() => body)
  }

  def send_wait_dispatcher(body: => Unit): Unit =
  {
    if (dispatcher.check_thread) body
    else dispatcher.send_wait(() => body)
  }


  /* outlets */

  val statistics = new Session.Outlet[Session.Statistics](dispatcher)
  val global_options = new Session.Outlet[Session.Global_Options](dispatcher)
  val caret_focus = new Session.Outlet[Session.Caret_Focus.type](dispatcher)
  val raw_edits = new Session.Outlet[Session.Raw_Edits](dispatcher)
  val commands_changed = new Session.Outlet[Session.Commands_Changed](dispatcher)
  val phase_changed = new Session.Outlet[Session.Phase](dispatcher)
  val syslog_messages = new Session.Outlet[Prover.Output](dispatcher)
  val raw_output_messages = new Session.Outlet[Prover.Output](dispatcher)
  val trace_events = new Session.Outlet[Simplifier_Trace.Event.type](dispatcher)
  val debugger_updates = new Session.Outlet[Debugger.Update.type](dispatcher)

  val all_messages = new Session.Outlet[Prover.Message](dispatcher)  // potential bottle-neck!


  /** main protocol manager **/

  /* internal messages */

  private case class Start(start_prover: Prover.Receiver => Prover)
  private case object Stop
  private case class Cancel_Exec(exec_id: Document_ID.Exec)
  private case class Protocol_Command(name: String, args: List[String])
  private case class Update_Options(options: Options)
  private case object Consolidate_Execution
  private case object Prune_History


  /* phase */

  private def post_phase(new_phase: Session.Phase): Session.Phase =
  {
    phase_changed.post(new_phase)
    new_phase
  }
  private val _phase = Synchronized[Session.Phase](Session.Inactive)
  private def phase_=(new_phase: Session.Phase): Unit = _phase.change(_ => post_phase(new_phase))

  def phase = _phase.value
  def is_ready: Boolean = phase == Session.Ready


  /* global state */

  private val syslog = new Session.Syslog(syslog_limit)
  def syslog_content(): String = syslog.content

  private val global_state = Synchronized(Document.State.init)
  def current_state(): Document.State = global_state.value

  def recent_syntax(name: Document.Node.Name): Outer_Syntax =
    global_state.value.recent_finished.version.get_finished.nodes(name).syntax getOrElse
    resources.session_base.overall_syntax


  /* pipelined change parsing */

  private case class Text_Edits(
    previous: Future[Document.Version],
    doc_blobs: Document.Blobs,
    text_edits: List[Document.Edit_Text],
    consolidate: List[Document.Node.Name],
    version_result: Promise[Document.Version])

  private val change_parser = Consumer_Thread.fork[Text_Edits]("change_parser", daemon = true)
  {
    case Text_Edits(previous, doc_blobs, text_edits, consolidate, version_result) =>
      val prev = previous.get_finished
      val change =
        Timing.timeit("parse_change", timing) {
          resources.parse_change(reparse_limit, prev, doc_blobs, text_edits, consolidate)
        }
      version_result.fulfill(change.version)
      manager.send(change)
      true
  }


  /* buffered changes */

  private object change_buffer
  {
    private var assignment: Boolean = false
    private var nodes: Set[Document.Node.Name] = Set.empty
    private var commands: Set[Command] = Set.empty

    def flush(): Unit = synchronized {
      if (assignment || nodes.nonEmpty || commands.nonEmpty)
        commands_changed.post(Session.Commands_Changed(assignment, nodes, commands))
      if (nodes.nonEmpty) consolidation.update(nodes)
      assignment = false
      nodes = Set.empty
      commands = Set.empty
    }
    private val delay_flush = Standard_Thread.delay_first(output_delay) { flush() }

    def invoke(assign: Boolean, edited_nodes: List[Document.Node.Name], cmds: List[Command]): Unit =
      synchronized {
        assignment |= assign
        for (node <- edited_nodes) {
          nodes += node
        }
        for (command <- cmds) {
          nodes += command.node_name
          command.blobs_names.foreach(nodes += _)
          commands += command
        }
        delay_flush.invoke()
      }

    def shutdown()
    {
      delay_flush.revoke()
      flush()
    }
  }


  /* postponed changes */

  private object postponed_changes
  {
    private var postponed: List[Session.Change] = Nil

    def store(change: Session.Change): Unit = synchronized { postponed ::= change }

    def flush(state: Document.State): List[Session.Change] = synchronized {
      val (assigned, unassigned) = postponed.partition(change => state.is_assigned(change.previous))
      postponed = unassigned
      assigned.reverse
    }
  }


  /* node consolidation */

  private object consolidation
  {
    private val delay =
      Standard_Thread.delay_first(consolidate_delay) { manager.send(Consolidate_Execution) }

    private val init_state: Option[Set[Document.Node.Name]] = Some(Set.empty)
    private val state = Synchronized(init_state)

    def exit()
    {
      delay.revoke()
      state.change(_ => None)
    }

    def update(new_nodes: Set[Document.Node.Name] = Set.empty)
    {
      val active =
        state.change_result(st =>
          (st.isDefined, st.map(nodes => if (nodes.isEmpty) new_nodes else nodes ++ new_nodes)))
      if (active) delay.invoke()
    }

    def flush(): Set[Document.Node.Name] =
      state.change_result(st => if (st.isDefined) (st.get, init_state) else (Set.empty, None))
  }


  /* prover process */

  private object prover
  {
    private val variable = Synchronized[Option[Prover]](None)

    def defined: Boolean = variable.value.isDefined
    def get: Prover = variable.value.get
    def set(p: Prover) { variable.change(_ => Some(p)) }
    def reset { variable.change(_ => None) }
    def await_reset() { variable.guarded_access({ case None => Some((), None) case _ => None }) }
  }


  /* file formats */

  lazy val file_formats: File_Format.Session =
    resources.file_formats.start_session(session)


  /* protocol handlers */

  private val protocol_handlers = Protocol_Handlers.init(session)

  def get_protocol_handler(name: String): Option[Session.Protocol_Handler] =
    protocol_handlers.get(name)

  def init_protocol_handler(handler: Session.Protocol_Handler): Unit =
    protocol_handlers.init(handler)

  def init_protocol_handler(name: String): Unit =
    protocol_handlers.init(name)


  /* debugger */

  private val debugger_handler = new Debugger.Handler(this)
  init_protocol_handler(debugger_handler)

  def debugger: Debugger = debugger_handler.debugger


  /* manager thread */

  private val delay_prune =
    Standard_Thread.delay_first(prune_delay) { manager.send(Prune_History) }

  private val manager: Consumer_Thread[Any] =
  {
    /* raw edits */

    def handle_raw_edits(
      doc_blobs: Document.Blobs = Document.Blobs.empty,
      edits: List[Document.Edit_Text] = Nil,
      consolidate: List[Document.Node.Name] = Nil)
    //{{{
    {
      require(prover.defined)

      prover.get.discontinue_execution()

      val previous = global_state.value.history.tip.version
      val version = Future.promise[Document.Version]
      global_state.change(_.continue_history(previous, edits, version))

      raw_edits.post(Session.Raw_Edits(doc_blobs, edits))
      change_parser.send(Text_Edits(previous, doc_blobs, edits, consolidate, version))
    }
    //}}}


    /* resulting changes */

    def handle_change(change: Session.Change)
    //{{{
    {
      require(prover.defined)

      def id_command(command: Command)
      {
        for {
          (name, digest) <- command.blobs_defined
          if !global_state.value.defined_blob(digest)
        } {
          change.version.nodes(name).get_blob match {
            case Some(blob) =>
              global_state.change(_.define_blob(digest))
              prover.get.define_blob(digest, blob.bytes)
            case None =>
              Output.error_message("Missing blob " + quote(name.toString))
          }
        }

        if (!global_state.value.defined_command(command.id)) {
          global_state.change(_.define_command(command))
          prover.get.define_command(command)
        }
      }
      for { (_, edit) <- change.doc_edits } {
        edit.foreach({ case (c1, c2) => c1.foreach(id_command); c2.foreach(id_command) })
      }

      val assignment = global_state.value.the_assignment(change.previous).check_finished
      global_state.change(_.define_version(change.version, assignment))
      prover.get.update(change.previous.id, change.version.id, change.doc_edits, change.consolidate)
      resources.commit(change)
    }
    //}}}


    /* prover output */

    def handle_output(output: Prover.Output)
    //{{{
    {
      def bad_output()
      {
        if (verbose)
          Output.warning("Ignoring bad prover output: " + output.message.toString)
      }

      def change_command(f: Document.State => (Command.State, Document.State))
      {
        try {
          val st = global_state.change_result(f)
          change_buffer.invoke(false, Nil, List(st.command))
        }
        catch { case _: Document.State.Fail => bad_output() }
      }

      output match {
        case msg: Prover.Protocol_Output =>
          val handled = protocol_handlers.invoke(msg)
          if (!handled) {
            msg.properties match {
              case Markup.Protocol_Handler(name) if prover.defined =>
                init_protocol_handler(name)

              case Protocol.Command_Timing(state_id, timing) if prover.defined =>
                val message = XML.elem(Markup.STATUS, List(XML.Elem(Markup.Timing(timing), Nil)))
                change_command(_.accumulate(state_id, xml_cache.elem(message), xml_cache))

              case Protocol.Theory_Timing(_, _) =>
                // FIXME

              case Markup.Export(args)
              if args.id.isDefined && Value.Long.unapply(args.id.get).isDefined =>
                val id = Value.Long.unapply(args.id.get).get
                val export = Export.make_entry("", args, msg.bytes, cache = xz_cache)
                change_command(_.add_export(id, (args.serial, export)))

              case Markup.Assign_Update =>
                msg.text match {
                  case Protocol.Assign_Update(id, edited, update) =>
                    try {
                      val (edited_nodes, cmds) =
                        global_state.change_result(_.assign(id, edited, update))
                      change_buffer.invoke(true, edited_nodes, cmds)
                      manager.send(Session.Change_Flush)
                    }
                    catch { case _: Document.State.Fail => bad_output() }
                  case _ => bad_output()
                }
                delay_prune.invoke()

              case Markup.Removed_Versions =>
                msg.text match {
                  case Protocol.Removed(removed) =>
                    try {
                      global_state.change(_.removed_versions(removed))
                      manager.send(Session.Change_Flush)
                    }
                    catch { case _: Document.State.Fail => bad_output() }
                  case _ => bad_output()
                }

              case Markup.ML_Statistics(props) =>
                statistics.post(Session.Statistics(props))

              case Markup.Task_Statistics(props) =>
                // FIXME

              case _ => bad_output()
            }
          }
        case _ =>
          output.properties match {
            case Position.Id(state_id) =>
              change_command(_.accumulate(state_id, output.message, xml_cache))

            case _ if output.is_init =>
              prover.get.options(file_formats.prover_options(session_options))
              prover.get.session_base(resources)
              phase = Session.Ready
              debugger.ready()

            case Markup.Process_Result(result) if output.is_exit =>
              file_formats.stop_session
              phase = Session.Terminated(result)
              prover.reset

            case _ =>
              raw_output_messages.post(output)
          }
        }
    }
    //}}}


    /* main thread */

    Consumer_Thread.fork[Any]("Session.manager", daemon = true)
    {
      case arg: Any =>
        //{{{
        arg match {
          case output: Prover.Output =>
            if (output.is_stdout || output.is_stderr)
              raw_output_messages.post(output)
            else handle_output(output)

            if (output.is_syslog) {
              syslog += output.message
              syslog_messages.post(output)
            }

            all_messages.post(output)

          case input: Prover.Input =>
            all_messages.post(input)

          case Start(start_prover) if !prover.defined =>
            try {
              prover.set(start_prover(manager.send(_)))
            }
            catch {
              case NonFatal(e) =>
                // hack: up to 2016, the manager was informed about startup failures
                // we emulate this behaviour here
                manager.send(new Prover.Output(XML.Elem(Markup(Markup.EXIT, List()), Nil)))
            }

          case Stop =>
            consolidation.exit()
            delay_prune.revoke()
            if (prover.defined) {
              protocol_handlers.exit()
              global_state.change(_ => Document.State.init)
              prover.get.terminate
            }

          case Consolidate_Execution =>
            if (prover.defined) {
              val state = global_state.value
              state.stable_tip_version match {
                case None => consolidation.update()
                case Some(version) =>
                  val consolidate =
                    consolidation.flush().iterator.filter(name =>
                      !resources.session_base.loaded_theory(name) &&
                      !state.node_consolidated(version, name) &&
                      state.node_maybe_consolidated(version, name)).toList
                  if (consolidate.nonEmpty) handle_raw_edits(consolidate = consolidate)
              }
            }

          case Prune_History =>
            if (prover.defined) {
              val old_versions = global_state.change_result(_.remove_versions(prune_size))
              if (old_versions.nonEmpty) prover.get.remove_versions(old_versions)
            }

          case Update_Options(options) =>
            if (prover.defined && is_ready) {
              prover.get.options(file_formats.prover_options(options))
              handle_raw_edits()
            }
            global_options.post(Session.Global_Options(options))

          case Cancel_Exec(exec_id) if prover.defined =>
            prover.get.cancel_exec(exec_id)

          case Session.Raw_Edits(doc_blobs, edits) if prover.defined =>
            handle_raw_edits(doc_blobs = doc_blobs, edits = edits)

          case Session.Dialog_Result(id, serial, result) if prover.defined =>
            prover.get.dialog_result(serial, result)
            handle_output(new Prover.Output(Protocol.Dialog_Result(id, serial, result)))

          case Protocol_Command(name, args) if prover.defined =>
            prover.get.protocol_command(name, args:_*)

          case change: Session.Change if prover.defined =>
            val state = global_state.value
            if (!state.removing_versions && state.is_assigned(change.previous))
              handle_change(change)
            else postponed_changes.store(change)

          case Session.Change_Flush if prover.defined =>
            val state = global_state.value
            if (!state.removing_versions)
              postponed_changes.flush(state).foreach(handle_change(_))

          case bad =>
            if (verbose) Output.warning("Ignoring bad message: " + bad.toString)
        }
        true
        //}}}
    }
  }


  /* main operations */

  def snapshot(name: Document.Node.Name = Document.Node.Name.empty,
      pending_edits: List[Text.Edit] = Nil): Document.Snapshot =
    global_state.value.snapshot(name, pending_edits)

  @tailrec final def await_stable_snapshot(): Document.Snapshot =
  {
    val snapshot = this.snapshot()
    if (snapshot.is_outdated) {
      Thread.sleep(output_delay.ms)
      await_stable_snapshot()
    }
    else snapshot
  }

  def start(start_prover: Prover.Receiver => Prover)
  {
    file_formats
    _phase.change(
      {
        case Session.Inactive =>
          manager.send(Start(start_prover))
          post_phase(Session.Startup)
        case phase => error("Cannot start prover in phase " + quote(phase.print))
      })
  }

  def send_stop()
  {
    val was_ready =
      _phase.guarded_access(phase =>
        phase match {
          case Session.Startup | Session.Shutdown => None
          case Session.Terminated(_) => Some((false, phase))
          case Session.Inactive => Some((false, post_phase(Session.Terminated(Process_Result(0)))))
          case Session.Ready => Some((true, post_phase(Session.Shutdown)))
        })
    if (was_ready) manager.send(Stop)
  }

  def stop(): Process_Result =
  {
    send_stop()
    prover.await_reset()

    change_parser.shutdown()
    change_buffer.shutdown()
    manager.shutdown()
    dispatcher.shutdown()

    phase match {
      case Session.Terminated(result) => result
      case phase => error("Bad session phase after shutdown: " + quote(phase.print))
    }
  }

  def protocol_command(name: String, args: String*)
  { manager.send(Protocol_Command(name, args.toList)) }

  def cancel_exec(exec_id: Document_ID.Exec)
  { manager.send(Cancel_Exec(exec_id)) }

  def update(doc_blobs: Document.Blobs, edits: List[Document.Edit_Text])
  { if (edits.nonEmpty) manager.send_wait(Session.Raw_Edits(doc_blobs, edits)) }

  def update_options(options: Options)
  { manager.send_wait(Update_Options(options)) }

  def dialog_result(id: Document_ID.Generic, serial: Long, result: String)
  { manager.send(Session.Dialog_Result(id, serial, result)) }
}
