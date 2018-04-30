package info.hupel.isabelle.impl

import java.nio.file.Path

import scala.concurrent.ExecutionContext

import info.hupel.isabelle.api

import shapeless._
import shapeless.tag._

@api.Implementation(identifier = "2016")
final class Environment private(context: api.Environment.Context) extends api.Environment(context) {

  isabelle.Standard_Thread.pool = context.executorService
  try {
    isabelle.Isabelle_System.init(
      isabelle_root = home.toString,
      cygwin_root = home.resolve("contrib/cygwin").toString,
      user = user.toString,
      init_env = variables,
      hook = setEtcComponents _
    )
  }
  finally {
    cleanEtcComponents()
  }

  private def destMarkup(markup: isabelle.Markup) =
    (markup.name, markup.properties)

  protected[isabelle] val exitTag = isabelle.Markup.EXIT
  protected[isabelle] val functionTag = isabelle.Markup.FUNCTION
  protected[isabelle] val initTag = isabelle.Markup.INIT
  protected[isabelle] val protocolTag = isabelle.Markup.PROTOCOL
  protected[isabelle] val printTags = isabelle.Markup.messages.keySet

  protected[isabelle] type Session = isabelle.Session

  private lazy val options =
    context.options.foldLeft(isabelle.Options.init()) { (options, update) =>
      options + (update.key, update.value)
    }

  private def mkPaths(paths: List[Path]) =
    paths.map(p => isabelle.Path.explode(isabellePath(p.toAbsolutePath.toString)))

  private def progress(config: api.Configuration) = new isabelle.Progress {
    logger.debug(s"Building $config ...")
    override def echo(msg: String) = logger.debug(s"${config.session}: $msg")
    override def theory(session: String, theory: String) = logger.debug(s"${config.session}: theory $theory ($session)")
  }

  protected[isabelle] def build(config: api.Configuration) =
    isabelle.Build.build(
      options = options,
      progress = progress(config),
      build_heap = true,
      dirs = mkPaths(config.paths),
      verbose = true,
      sessions = List(config.session)
    )

  protected[isabelle] def create(config: api.Configuration, consumer: (api.Markup, api.XML.Body) => Unit) = {
    val content = isabelle.Build.session_content(options, false, mkPaths(config.paths), config.session)
    val resources = new isabelle.Resources(content.loaded_theories, content.known_theories, content.syntax)

    val use = protocolTheory(new isabelle.Thy_Info(resources).Dependencies.empty.loaded_theories) map { thy =>
      s"""use_thy ${isabelle.ML_Syntax.print_string0(thy)};"""
    }

    def convertXML(tree: isabelle.XML.Tree): api.XML.Tree = tree match {
      case isabelle.XML.Text(content) => api.XML.text(content)
      case isabelle.XML.Elem(markup, body) => api.XML.elem(destMarkup(markup), body.map(convertXML))
    }

    val session = new isabelle.Session(resources)

    session.all_messages += isabelle.Session.Consumer[isabelle.Prover.Message]("firehose") {
      case msg: isabelle.Prover.Protocol_Output =>
        consumer(destMarkup(msg.message.markup), api.XML.bodyFromYXML(msg.text))
        logger.trace(msg.toString)
      case msg: isabelle.Prover.Output =>
        consumer(destMarkup(msg.message.markup), msg.message.body.map(convertXML))
        logger.trace(msg.toString)
      case msg =>
        logger.trace(msg.toString)
    }
    val ml = s"""
      Isabelle_Process.protocol_command "$evalCommand" (List.app (use_text ML_Env.local_context {debug=false, file="eval", line=0, verbose=true}));
    """

    session.start("Isabelle" /* name is ignored anyway */, List("-r", "-e", ml, "-q", config.session))

    // Contrary to Isabelle2016-1, Isabelle2016 doesn't like loading theories
    // without having options sent first. Postpone evaluating the `use_thy`
    // expression until after startup. The downside is that we get no feedback
    // when the theory is done loading, but this should not be a problem:
    // protocol commands are processed sequentially, so by the time we send the
    // next command after loading, the theory should be available. Still, the
    // timeout in `System.create` must be big enough to allow the theory to be
    // processed.
    (session, use)
  }

  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]) = {
    if (session.phase != isabelle.Session.Ready)
      sys.error("session not ready")
    session.protocol_command(name, args: _*)
  }

  protected[isabelle] def sendOptions(session: Session) =
    session.protocol_command("Prover.options", isabelle.YXML.string_of_body(options.encode))

  protected[isabelle] def dispose(session: Session) = session.stop()

  def isabelleSetting(name: String): String =
    isabelle.Isabelle_System.getenv(name)

  def isabellePath(path: String): String =
    isabelle.File.standard_path(path)

  def decode(text: String @@ api.Environment.Raw): String @@ api.Environment.Unicode = tag.apply(isabelle.Symbol.decode(text))
  def encode(text: String @@ api.Environment.Unicode): String @@ api.Environment.Raw = tag.apply(isabelle.Symbol.encode(text))

  def exec(tool: String, args: List[String]) = {
    val (out, rc) = isabelle.Isabelle_System.isabelle_tool(tool, args: _*)
    print(out)
    rc
  }

}
