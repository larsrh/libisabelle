/*  Title:      Pure/System/isabelle_process.scala
    Author:     Makarius

Isabelle process wrapper.
*/

package isabelle


import java.io.{File => JFile}


object Isabelle_Process
{
  def start(session: Session,
    options: Options,
    logic: String = "",
    args: List[String] = Nil,
    dirs: List[Path] = Nil,
    modes: List[String] = Nil,
    pre_eval: List[String] = Nil,
    cwd: JFile = null,
    env: Map[String, String] = Isabelle_System.settings(),
    sessions_structure: Option[Sessions.Structure] = None,
    store: Option[Sessions.Store] = None,
    phase_changed: Session.Phase => Unit = null)
  {
    if (phase_changed != null)
      session.phase_changed += Session.Consumer("Isabelle_Process")(phase_changed)

    session.start(receiver =>
      Isabelle_Process(options, logic = logic, args = args, dirs = dirs, modes = modes,
        pre_eval = pre_eval, cwd = cwd, env = env, receiver = receiver, xml_cache = session.xml_cache,
        sessions_structure = sessions_structure, store = store))
  }

  def apply(
    options: Options,
    logic: String = "",
    args: List[String] = Nil,
    dirs: List[Path] = Nil,
    modes: List[String] = Nil,
    pre_eval: List[String] = Nil,
    cwd: JFile = null,
    env: Map[String, String] = Isabelle_System.settings(),
    receiver: Prover.Receiver = (msg: Prover.Message) => Output.writeln(msg.toString, stdout = true),
    xml_cache: XML.Cache = XML.make_cache(),
    sessions_structure: Option[Sessions.Structure] = None,
    store: Option[Sessions.Store] = None): Prover =
  {
    val channel = System_Channel()
    val process =
      try {
        val channel_options =
          options.string.update("system_channel_address", channel.address).
            string.update("system_channel_password", channel.password)
        ML_Process(channel_options, logic = logic, args = args, dirs = dirs, modes = modes,
            pre_eval = pre_eval, cwd = cwd, env = env, sessions_structure = sessions_structure, store = store)
      }
      catch { case exn @ ERROR(_) => channel.shutdown(); throw exn }
    process.stdin.close

    new Prover(receiver, xml_cache, channel, process)
  }
}
