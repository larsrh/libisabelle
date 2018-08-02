package info.hupel.isabelle.impl

import java.nio.file.Path

import scala.concurrent.ExecutionContext

import info.hupel.isabelle.api

import shapeless._
import shapeless.tag._

@api.Implementation(identifier = "2018-RC3")
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
    ).rc

  protected[isabelle] def create(config: api.Configuration, consumer: (api.Markup, api.XML.Body) => Unit) = {
    val base: isabelle.Sessions.Base /* FIXME recursive value?! */ = isabelle.Sessions.base_info(
      options = options,
      session = config.session,
      dirs = mkPaths(config.paths),
      all_known = true
    ).check_base
    val resources = new isabelle.Resources(base)

    val use = protocolTheory(resources.Dependencies.empty.loaded_theories.keys_iterator.toSet) map { thy =>
      s"""use_thy ${isabelle.ML_Syntax.print_string_bytes(thy)};"""
    }

    def convertXML(tree: isabelle.XML.Tree): api.XML.Tree = tree match {
      case isabelle.XML.Text(content) => api.XML.text(content)
      case isabelle.XML.Elem(markup, body) => api.XML.elem(destMarkup(markup), body.map(convertXML))
    }

    val session = new isabelle.Session(options, resources)

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

    session.start(receiver =>
      isabelle.Isabelle_Process(
        options,
        logic = config.session,
        receiver = receiver,
        pre_eval = List(s"""Isabelle_Process.protocol_command "$evalCommand" (List.app (ML o Input.string))"""),
        dirs = mkPaths(config.paths)))
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
    try {
      isabelle.Isabelle_Tool.main((tool :: args).toArray)
      0
    }
    catch {
      case api.ExitTrap(rc) => rc
    }
  }

}
