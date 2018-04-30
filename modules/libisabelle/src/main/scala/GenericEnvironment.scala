package info.hupel.isabelle

import scala.concurrent.duration._
import scala.sys.process._

import monix.execution.cancelables.OrderedCancelable

import shapeless.tag._

import info.hupel.isabelle.api._

object GenericEnvironment {

  sealed abstract class Error { def explain: String }
  case object WindowsUnsupported extends Error {
    val explain = "Generic environment does not support Windows"
  }
  case object NotOfficial extends Error {
    val explain = "Generic environment does not support unofficial platform"
  }

  def apply(context: Environment.Context, version: Version, platform: Platform): Either[Error, GenericEnvironment] = platform match {
    case Platform.Linux | Platform.OSX =>
      Right(new GenericEnvironment(context, version))
    case Platform.Windows =>
      Left(WindowsUnsupported)
    case _ =>
      Left(NotOfficial)
  }

}

final class GenericEnvironment private(context: Environment.Context, version: Version) extends Environment(context, Some(version), false) {

  if (context.options.nonEmpty)
    logger.warn("Ignoring options")

  protected[isabelle] val exitTag = ""
  protected[isabelle] val functionTag = ""
  protected[isabelle] val initTag = ""
  protected[isabelle] val protocolTag = ""
  protected[isabelle] val printTags = Set.empty[String]

  protected[isabelle] type Session = Unit

  protected[isabelle] def build(config: Configuration) = {
    val dirs = config.paths.flatMap(p => List("-d", p.toString))
    val cli = "-bv" :: dirs ::: List(config.session)
    exec("build", cli)
  }

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XML.Body) => Unit) = ???
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]) = ???
  protected[isabelle] def sendOptions(session: Session) = ???
  protected[isabelle] def dispose(session: Session) = ???

  def isabelleSetting(name: String): String = ???
  def isabellePath(path: String): String = path

  def decode(text: String @@ Environment.Raw): String @@ Environment.Unicode = ???
  def encode(text: String @@ Environment.Unicode): String @@ Environment.Raw = ???

  def exec(tool: String, args: List[String]) = {
    setEtcComponents()

    val binary = home.resolve("bin").resolve("isabelle")
    val console = ProcessLogger(fout = Console.out.println, ferr = Console.err.println)

    logger.debug(s"Executing '$tool' with arguments '${args.mkString(" ")}' ...")

    val env = (variables ++ Map("USER_HOME" -> user.toString)).toList
    val c = OrderedCancelable()

    try {
      val proc = Process(binary.toString :: tool :: args, None, env: _*).run(console)
      c := scheduler.scheduleOnce(5.seconds) {
        logger.info("Opportunistically cleaning components ...")
        cleanEtcComponents()
      }
      proc.exitValue()
    }
    finally {
      cleanEtcComponents()
      c.cancel()
    }
  }

}
