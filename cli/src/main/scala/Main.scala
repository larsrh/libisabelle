package edu.tum.cs.isabelle.cli

import java.nio.file._

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import org.log4s._

import cats.data.Xor

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup._

import acyclic.file

// FIXME rewrite using alexarchambault/case-app
object Args {

  private def parse(args: Args, rest: List[String]): Option[Args] = rest match {
    case "--version" :: version :: rest if args.version.isEmpty => parse(args.copy(version = Some(Version(version))), rest)
    case "--include" :: path :: rest => parse(args.copy(include = Paths.get(path) :: args.include), rest)
    case "--session" :: session :: rest if args.session.isEmpty => parse(args.copy(session = Some(session)), rest)
    case "--home" :: home :: rest if args.home.isEmpty => parse(args.copy(home = Some(Paths.get(home))), rest)
    case "--dump" :: dump :: rest if args.dump.isEmpty => parse(args.copy(dump = Some(Paths.get(dump))), rest)
    case cmd :: rest if !cmd.startsWith("-") => Some(args.copy(command = Some(cmd), rest = rest))
    case Nil => Some(args)
    case _ => None
  }

  def parse(args: List[String]): Option[Args] =
    parse(Args(None, Nil, None, None, None, None, Nil), args)

  val usage = """
    | Usage: [--version VERSION] [--include PATH]* [--session SESSION] [--home PATH] [--dump PATH] [CMD [extra options ...]]
    |
    | Available commands:
    |   build
    |   check
    |   jedit
    |   report [--format FORMAT] FILE [FILES ...]
    |
    | Available formats:
    |   raw-xml (default)
    |   x-ray
    |""".stripMargin

}

case class Args(
  version: Option[Version],
  include: List[Path],
  session: Option[String],
  home: Option[Path],
  dump: Option[Path],
  command: Option[String],
  rest: List[String]
)

object Main {

  private val logger = getLogger(getClass)

  val commands: Map[String, Command] = Map(
    "build" -> Build,
    "check" -> Check,
    "jedit" -> JEdit,
    "report" -> Report
  )

  /**
   * Guesses an Isabelle [[edu.tum.cs.isabelle.api.Version version]] from
   * the system environment.
   *
   * The following is tried, in order:
   * - the environment variable `ISABELLE_VERSION`
   * - Java system property `edu.tum.cs.isabelle.version`
   */
  def guessVersion: Option[Version] =
    Option(System.getenv("ISABELLE_VERSION")).orElse(
      Option(System.getProperty("edu.tum.cs.isabelle.version"))
    ).map(Version)

  def guessPlatform: Platform = Setup.defaultPlatform match {
    case Some(platform) => platform
    case None =>
      logger.debug("Falling back to generic platform, will write temporary data into `./libisabelle`")
      Platform.genericPlatform(Paths.get("libisabelle"))
  }

  def main(args: Array[String]): Unit = Args.parse(args.toList) match {
    case Some(args) =>
      val version = args.version.orElse(guessVersion).getOrElse {
        Console.err.println(Args.usage)
        sys.error("no version specified")
      }

      val dump = args.dump.getOrElse(Files.createTempDirectory("libisabelle_resources"))
      val resources = Resources.dumpIsabelleResources(dump, getClass.getClassLoader)

      val configuration = args.session match {
        case Some(session) => resources.makeConfiguration(args.include, session)
        case None => resources.makeConfiguration(Nil, "Protocol")
      }

      logger.info(s"Using $configuration")

      def mkSetup = args.home match {
        case None =>
          Setup.defaultSetup(version) match {
            case Xor.Right(setup) => setup
            case Xor.Left(reason) => sys.error(reason.explain)
          }
        case Some(home) => Future.successful(Setup(home, guessPlatform, version, Setup.defaultPackageName))
      }

      lazy val bundle =
        for {
          setup <- mkSetup
          env <- setup.makeEnvironment
        }
        yield Bundle(env, setup, configuration)

      val app = args.command match {
        case None => bundle.map(_ => ())
        case Some(cmd) =>
          commands.get(cmd) match {
            case None =>
              Console.err.println(Args.usage)
              sys.error(s"no such command `$cmd`")
            case Some(cmd) =>
              bundle.flatMap(cmd.run(_, args.rest))
          }
      }

      Await.result(app, Duration.Inf)
    case None =>
      Console.err.println(Args.usage)
      sys.error("invalid arguments")
  }

}
