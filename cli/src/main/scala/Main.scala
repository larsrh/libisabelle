package edu.tum.cs.isabelle.cli

import java.nio.file.{Path, Paths}

import scala.concurrent._
import scala.concurrent.duration.Duration
import scala.concurrent.ExecutionContext.Implicits.global

import org.log4s._

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup.Setup

import acyclic.file

object Args {

  private def parse(args: Args, rest: List[String]): Option[Args] = rest match {
    case "--version" :: version :: rest if args.version.isEmpty => parse(args.copy(version = Some(Version(version))), rest)
    case "--include" :: path :: rest => parse(args.copy(include = Paths.get(path) :: args.include), rest)
    case "--session" :: session :: rest if args.session.isEmpty => parse(args.copy(session = Some(session)), rest)
    case cmd :: rest if !cmd.startsWith("-") => Some(args.copy(command = Some(cmd), rest = rest))
    case Nil => Some(args)
    case _ => None
  }

  def parse(args: List[String]): Option[Args] =
    parse(Args(None, Nil, None, None, Nil), args)

  val usage = """
    | Usage: [--version VERSION] [--include PATH]* [--session SESSION] [CMD [extra options ...]]
    |
    | Available commands:
    |   build
    |   jedit
    |   report FILE [FILES ...]
    |""".stripMargin

}

case class Args(
  version: Option[Version],
  include: List[Path],
  session: Option[String],
  command: Option[String],
  rest: List[String]
)

object Main {

  private val logger = getLogger(getClass)

  val commands: Map[String, Command] = Map(
    "build" -> Build,
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

  def main(args: Array[String]): Unit = Args.parse(args.toList) match {
    case Some(args) =>
      val version = args.version.orElse(guessVersion).getOrElse {
        Console.err.println(Args.usage)
        sys.error("no version specified")
      }

      val configuration = args.session match {
        case Some(session) => Configuration(args.include, session)
        case None => Configuration.fromPath(Paths.get("."), "Protocol")
      }

      logger.info(s"Using $configuration")

      lazy val bundle =
        for {
          setup <- Setup.defaultSetup(version)
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
