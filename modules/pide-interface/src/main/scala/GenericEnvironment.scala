package info.hupel.isabelle.api

import scala.sys.process._

import shapeless._
import shapeless.tag._

final class GenericEnvironment(context: Environment.Context) extends Environment(context) {

  protected[isabelle] val exitTag = ""
  protected[isabelle] val functionTag = ""
  protected[isabelle] val initTag = ""
  protected[isabelle] val protocolTag = ""
  protected[isabelle] val printTags = Set.empty[String]

  protected[isabelle] type Session = Unit

  protected[isabelle] def isabelleSetting(name: String): String = ???
  protected[isabelle] def isabellePath(path: String): String = path

  protected[isabelle] def build(config: Configuration) = {
    val dirs = config.paths.flatMap(p => List("-d", p.toString))
    val cli = "-bv" :: dirs ::: List(config.session)
    exec("build", cli)
  }

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XML.Body) => Unit) = ???
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]) = ???
  protected[isabelle] def sendOptions(session: Session) = ???
  protected[isabelle] def dispose(session: Session) = ???

  def decode(text: String @@ Environment.Raw): String @@ Environment.Unicode = ???
  def encode(text: String @@ Environment.Unicode): String @@ Environment.Raw = ???

  def exec(tool: String, args: List[String]) = context.platform match {
    case Platform.Linux | Platform.OSX =>
      setEtcComponents()

      val binary = home.resolve("bin").resolve("isabelle")
      val console = ProcessLogger(fout = Console.out.println, ferr = Console.err.println)

      logger.debug(s"Executing '$tool' with arguments '${args.mkString(" ")}' ...")

      val env = (variables ++ Map("USER_HOME" -> user.toString)).toList

      try {
        Process(binary.toString :: tool :: args, None, env: _*).run(console).exitValue()
      }
      finally {
        cleanEtcComponents()
      }
    case Platform.Windows =>
      logger.error(s"Generic Isabelle can't be started under Windows")
      sys.error("unsupported")
    case _ =>
      logger.error(s"Generic Isabelle can't be started under unofficial platforms")
      sys.error("unsupported")
  }

}
