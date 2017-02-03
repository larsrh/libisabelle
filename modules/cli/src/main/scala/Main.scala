package info.hupel.isabelle.cli

import java.net.URLClassLoader
import java.nio.file._

import org.apache.commons.io.FileUtils

import scala.concurrent._
import scala.concurrent.duration.Duration

import monix.execution.{Cancelable, CancelableFuture}
import monix.execution.Scheduler.Implicits.global

import org.log4s._

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._

import coursier.Dependency
import coursier.util.Parse

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

// FIXME rewrite using alexarchambault/case-app
object Args {

  def parse(args: Args, rest: List[String]): Option[Args] = rest match {
    case "--version" :: version :: rest if args.version.isEmpty => parse(args.copy(version = Some(Version(version))), rest)
    case "--session" :: session :: rest if args.session.isEmpty => parse(args.copy(session = Some(session)), rest)
    case "--include" :: path :: rest => parse(args.copy(include = Paths.get(path) :: args.include), rest)
    case "--component" :: path :: rest => parse(args.copy(components = Paths.get(path) :: args.components), rest)
    case "--home" :: home :: rest if args.home.isEmpty => parse(args.copy(home = Some(Paths.get(home))), rest)
    case "--user" :: user :: rest if args.user.isEmpty => parse(args.copy(user = Some(Paths.get(user))), rest)
    case "--fresh-user" :: rest if args.user.isEmpty => parse(args.copy(user = Some(Files.createTempDirectory("libisabelle_user"))), rest)
    case "--resources" :: resources :: rest if args.resources.isEmpty => parse(args.copy(resources = Some(Paths.get(resources))), rest)
    case "--fresh-resources" :: rest if args.resources.isEmpty => parse(args.copy(resources = Some(Files.createTempDirectory("libisabelle_resources"))), rest)
    case "--internal" :: rest if !args.internal => parse(args.copy(internal = true), rest)
    case "--fetch" :: fetch :: rest => parse(args.copy(fetch = fetch :: args.fetch), rest)
    case cmd :: rest if !cmd.startsWith("-") => Some(args.copy(command = Some(cmd), rest = rest))
    case Nil => Some(args)
    case _ => None
  }

  val usage = """
    | Usage:
    |   isabellectl [--version VERSION] [--session SESSION]
    |               [--include PATH]* [--component PATH]*
    |               [--home PATH]
    |               [--user PATH | --fresh-user]
    |               [--resources PATH | --fresh-resources]
    |               [--internal]
    |               [--fetch COORDINATE]*
    |               [CMD [extra options ...]]
    |
    | Available commands:
    |   build
    |   check
    |   exec [CMD ...]
    |   jedit
    |   report [--format FORMAT] FILE [FILES ...]
    |
    | Available formats:
    |   raw-xml (default)
    |   x-ray
    |""".stripMargin

  val init: Args = Args(
    version = None,
    session = None,
    include = Nil,
    components = Nil,
    home = None,
    user = None,
    resources = None,
    internal = false,
    fetch = Nil,
    command = None,
    rest = Nil
  )
}

case class Args(
  version: Option[Version],
  session: Option[String],
  include: List[Path],
  components: List[Path],
  home: Option[Path],
  user: Option[Path],
  resources: Option[Path],
  internal: Boolean,
  fetch: List[String],
  command: Option[String],
  rest: List[String]
)

object Main {

  private val logger = getLogger(getClass)

  val commands: Map[String, Command] = Map(
    "build" -> Build,
    "check" -> Check,
    "exec" -> Exec,
    "jedit" -> JEdit,
    "report" -> Report
  )

  /**
   * Guesses an Isabelle [[info.hupel.isabelle.api.Version version]] from
   * the system environment.
   *
   * The following is tried, in order:
   * - the environment variable `ISABELLE_VERSION`
   * - Java system property `info.hupel.isabelle.version`
   */
  def guessVersion: Option[Version] =
    Option(System.getenv("ISABELLE_VERSION")).orElse(
      Option(System.getProperty("info.hupel.isabelle.version"))
    ).map(Version)

  def guessPlatform: Platform = Platform.guess match {
    case Some(platform) => platform
    case None =>
      logger.debug("Falling back to generic platform, will write temporary data into `./libisabelle`")
      Platform.genericPlatform(Paths.get("libisabelle"))
  }

  def main(args: Array[String]): Unit = Args.parse(Args.init, args.toList) match {
    case Some(args) =>
      val version = args.version.orElse(guessVersion).getOrElse {
        Console.err.println(Args.usage)
        sys.error("no version specified")
      }

      val session = args.session.getOrElse("HOL")

      val user = args.user.getOrElse(guessPlatform.userStorage(version))

      val dump = args.resources.getOrElse(guessPlatform.resourcesStorage(version))
      FileUtils.deleteDirectory(dump.toFile)

      logger.info(s"Dumping resources to $dump ...")

      val parentClassLoader =
        if (args.internal) getClass.getClassLoader else null

      val classpath = args.fetch.traverseU(Parse.moduleVersion(_, BuildInfo.scalaBinaryVersion)) match {
        case Right(Nil) => Future.successful { Nil }
        case Right(modules) => guessPlatform.fetchArtifacts(modules.map { case (mod, v) => Dependency(mod, v) }.toSet)
        case Left(error) => sys.error(s"could not parse dependency: $error")
      }

      val resourceClassLoader = classpath map { files =>
        new URLClassLoader(files.map(_.toUri.toURL).toArray, parentClassLoader)
      }

      val configuration = Configuration(args.include, session)
      logger.info(s"Using $configuration")

      val components = resourceClassLoader map { classLoader =>
        Resources.dumpIsabelleResources(dump, classLoader) match {
          case Right(resources) =>
            resources.component :: args.components
          case Left(Resources.Absent) =>
            logger.warn("No resources on classpath")
            args.components
          case Left(error) =>
            sys.error(error.explain)
        }
      }

      lazy val setup = args.home match {
        case None =>
          Setup.default(version) match {
            case Right(setup) => setup
            case Left(reason) => sys.error(reason.explain)
          }
        case Some(home) =>
          Setup(home, guessPlatform, version)
      }

      lazy val bundle = for {
        cs <- CancelableFuture(components, Cancelable.empty)
        env <- setup.makeEnvironment(Resolver.Default, user, cs)
      } yield Bundle(env, setup, configuration)

      val app = args.command match {
        case None => bundle.map(_ => ())
        case Some(cmd) =>
          commands.get(cmd) match {
            case None =>
              Console.err.println(Args.usage)
              sys.error(s"no such command `$cmd`")
            case Some(cmd) =>
              bundle.flatMapC(cmd.cancelableRun(_, args.rest))
          }
      }

      Runtime.getRuntime().addShutdownHook(new Thread() {
        override def run(): Unit =
          logger.info("Shutting down ...")
          app.cancel()
      })
      Await.result(app, Duration.Inf)
    case None =>
      Console.err.println(Args.usage)
  }

}
