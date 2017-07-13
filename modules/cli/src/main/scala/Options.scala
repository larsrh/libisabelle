package info.hupel.isabelle.cli

import java.nio.file.{Files, Path, Paths}

import org.log4s._

import caseapp._

import io.rbricks.scalog._

import info.hupel.isabelle.Platform
import info.hupel.isabelle.api._

@AppName("libisabelle")
@AppVersion(BuildInfo.version)
@ProgName("isabellectl")
case class Options(

  @ValueDescription("version")
  @HelpMessage("Isabelle version ('stable:2016' or 'devel:isabelle-repo' for stable or devel; default prefix: 'stable')")
  @ExtraName("v")
  version: Version = Version.Stable("2016-1"),

  @ValueDescription("session")
  @HelpMessage("Isabelle session (directory needs to be known; either through --internal, --include, --component or --fetch)")
  @ExtraName("s")
  session: String = "HOL",

  @ValueDescription("path")
  @HelpMessage("Session directory (can be specified multiple times)")
  @ExtraName("i")
  include: List[Path],

  @ValueDescription("path")
  @HelpMessage("Component directory (can be specified multiple times)")
  @ExtraName("c")
  component: List[Path],

  @ValueDescription("path")
  @HelpMessage("Isabelle home directory (not checked against version; if unspecified, Isabelle will be downloaded if necessary; conflicts with --update)")
  home: Option[Path],

  @ValueDescription("path")
  @HelpMessage("Isabelle user directory root")
  user: Option[Path],

  @HelpMessage("use temporary user directory (conflicts with --user)")
  freshUser: Boolean = false,

  @ValueDescription("path")
  @HelpMessage("libisabelle resource directory")
  resources: Option[Path],

  @HelpMessage("use temporary resource directory (conflicts with --resources)")
  freshResources: Boolean = false,

  @HelpMessage("add libisabelle internal resources (required for many commands)")
  internal: Boolean = false,

  @ValueDescription("coordinate")
  @HelpMessage("add Maven artifact as resource (can be specified multiple times)")
  @ExtraName("f")
  fetch: List[String],

  @HelpMessage("add AFP as resource")
  afp: Boolean = false,

  @HelpMessage("verbose logging output")
  verbose: Boolean = false,

  @HelpMessage("initialize or update devel copy according to version identifier (requires devel version; conflicts with --home)")
  update: Boolean = false
) {

  def check(): Unit = {
    if (update) {
      version match {
        case Version.Stable(_) => Options.usageAndExit("Option conflict: --update requires devel version")
        case _ =>
      }

      home match {
        case Some(_) => Options.usageAndExit("Option conflict: --update only works if no --home is specified")
        case _ =>
      }
    }
  }

  lazy val userPath: Path = (user, freshUser) match {
    case (None, true) => Files.createTempDirectory("libisabelle_user")
    case (None, false) => Options.platform.userStorage(version)
    case (Some(path), false) => path
    case (Some(_), true) => Options.usageAndExit("Option conflict: --user and --fresh-user are mutually exclusive")
  }

  lazy val resourcePath: Path = (resources, freshResources) match {
    case (None, true) => Files.createTempDirectory("libisabelle_resources")
    case (None, false) => Options.platform.resourcesStorage(version)
    case (Some(path), false) => path
    case (Some(_), true) => Options.usageAndExit("Option conflict: --resources and --fresh-resources are mutually exclusive")
  }

  lazy val configuration = Configuration(include, session)

}

object Options {

  private lazy val logger = getLogger

  val commands: Map[String, Command] = Map(
    "build" -> Build,
    "check" -> Check,
    "exec" -> Exec,
    "jedit" -> JEdit,
    "report" -> Report
  )

  def usageAndExit(msg: String): Nothing = {
    Console.err.println(msg)
    CaseApp.printHelp[Options](err = true)
    Console.err.println(s"Available commands: ${commands.keys.mkString(" ")}")
    sys.exit(1)
  }

  lazy val platform: Platform = Platform.guess match {
    case Some(platform) => platform
    case None =>
      logger.debug("Falling back to generic platform, will write temporary data into `./libisabelle`")
      Platform.genericPlatform(Paths.get("libisabelle"))
  }

  def parse[T](args: List[String])(f: (Options, List[String]) => T): T = CaseApp.parseWithHelp[Options](args) match {
    case Right((options, help, usage, rest)) =>
      LoggingBackend.console("" -> {
        if (options.verbose)
          Level.Debug
        else
          Level.Warn
      })

      if (usage || help) {
        CaseApp.printHelp[Options](err = true)
        Console.err.println(s"Available commands: ${commands.keys.mkString(" ")}")
        sys.exit(0)
      }
      f(options, rest.toList)
    case Left(err) =>
      usageAndExit(err)
  }

}
