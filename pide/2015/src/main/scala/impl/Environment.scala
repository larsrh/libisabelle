package edu.tum.cs.isabelle.impl

import java.nio.file.Path
import java.util.concurrent.{Executors, ThreadFactory}

import scala.concurrent.ExecutionContext

import edu.tum.cs.isabelle.api

import shapeless._
import shapeless.tag._

@api.Implementation(identifier = "2015")
final class Environment private(context: api.Environment.Context) extends api.Environment(context) {

	isabelle.Future.execution_context = context.executorService
  isabelle.Isabelle_System.init(
    isabelle_home = home.toAbsolutePath.toString,
    cygwin_root = home.resolve("contrib/cygwin").toAbsolutePath.toString
  )

  api.Environment.patchSettings(isabelle.Isabelle_System, variables)

  private def destMarkup(markup: isabelle.Markup) =
    (markup.name, markup.properties)

  protected[isabelle] val exitTag = isabelle.Markup.EXIT
  protected[isabelle] val functionTag = isabelle.Markup.FUNCTION
  protected[isabelle] val initTag = isabelle.Markup.INIT
  protected[isabelle] val protocolTag = isabelle.Markup.PROTOCOL

  protected[isabelle] type Session = isabelle.Session

  private lazy val options = isabelle.Options.init()

  private def mkPaths(paths: List[Path]) =
    paths.map(p => isabelle.Path.explode(isabelle.Isabelle_System.posix_path(p.toAbsolutePath.toString)))

  private def progress(config: api.Configuration) = new isabelle.Build.Progress {
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
    val session = new isabelle.Session(resources)

    def convertXML(tree: isabelle.XML.Tree): api.XML.Tree = tree match {
      case isabelle.XML.Text(content) => api.XML.text(content)
      case isabelle.XML.Elem(markup, body) => api.XML.elem(destMarkup(markup), body.map(convertXML))
    }

    session.all_messages += isabelle.Session.Consumer[isabelle.Prover.Message]("firehose") {
      case msg: isabelle.Prover.Protocol_Output =>
        consumer(destMarkup(msg.message.markup), api.XML.bodyFromYXML(msg.text))
      case msg: isabelle.Prover.Output =>
        consumer(destMarkup(msg.message.markup), msg.message.body.map(convertXML))
      case _ =>
    }

    session.start("Isabelle" /* name is ignored anyway */, List("-r", "-q", config.session))
    session
  }

  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]) =
    session.protocol_command(name, args: _*)

  protected[isabelle] def sendOptions(session: Session) =
    session.protocol_command("Prover.options", isabelle.YXML.string_of_body(options.encode))

  protected[isabelle] def dispose(session: Session) = session.stop()

  def decode(text: String @@ api.Environment.Raw): String @@ api.Environment.Unicode = tag.apply(isabelle.Symbol.decode(text))
  def encode(text: String @@ api.Environment.Unicode): String @@ api.Environment.Raw = tag.apply(isabelle.Symbol.encode(text))

}
