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

import coursier.{Dependency, Module}
import coursier.util.Parse

import info.hupel.isabelle.Platform
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object Main {

  private lazy val logger = getLogger

  def main(args: Array[String]): Unit = Options.parse(args.toList) { (options, rest) =>
    options.check()

    val parentClassLoader =
      if (options.internal) getClass.getClassLoader else null

    val afp =
      if (options.afp)
        options.version match {
          case Version.Devel(_) =>
            Options.usageAndExit("Option conflict: devel version and --afp are mutually exclusive")
          case Version.Stable(identifier) =>
            Set(Dependency(Module(s"${BuildInfo.organization}.afp", s"afp-$identifier"), "1.1.+"))
        }
      else
        Set()

    val classpath = options.fetch.traverse(Parse.moduleVersion(_, BuildInfo.scalaBinaryVersion)) match {
      case Right(Nil) if !options.afp => Future.successful { Nil }
      case Right(modules) => Artifacts.fetch(Options.platform, modules.map { case (mod, v) => Dependency(mod, v) }.toSet ++ afp, options.offline)
      case Left(error) => sys.error(s"could not parse dependency: $error")
    }

    val resourceClassLoader = classpath map { files =>
      new URLClassLoader(files.map(_.toUri.toURL).toArray, parentClassLoader)
    }

    logger.info(s"Dumping resources to ${options.resourcePath} ...")
    if (Files.exists(options.resourcePath))
      FileUtils.cleanDirectory(options.resourcePath.toFile)

    val components = resourceClassLoader map { classLoader =>
      Resources.dumpIsabelleResources(options.resourcePath, classLoader) match {
        case Right(resources) =>
          resources.component :: options.component
        case Left(Resources.Absent) =>
          logger.warn("No resources on classpath")
          options.component
        case Left(error) =>
          sys.error(error.explain)
      }
    }

    val platform = Platform.guess.getOrElse(sys.error(Setup.UnknownPlatform.explain))

    lazy val setup = options.home match {
      case None =>
        Setup.detect(platform, options.version, options.update) match {
          case Right(setup) => setup
          case Left(Setup.Absent) if !options.offline => Setup.install(platform, options.version).fold(sys error _.explain, identity)
          case Left(e) => sys.error(e.explain)
        }
      case Some(home) =>
        Setup(home, Options.platform, options.version)
    }

    logger.info(s"Using ${options.configuration}")
    val updates = List(
      OptionKey.Integer("threads").set(Runtime.getRuntime.availableProcessors)
    )

    val resolver = Resolver.Classpath orElse new Resolver.Maven(options.offline)

    lazy val bundle = for {
      cs <- CancelableFuture(components, Cancelable.empty)
      env <- setup.makeEnvironment(resolver, options.userPath, cs, updates)
    } yield Bundle(env, setup, options.configuration)

    val app = rest match {
      case cmd :: rest =>
        Options.commands.get(cmd) match {
          case None =>
            Options.usageAndExit(s"no such command `$cmd`")
          case Some(cmd) =>
            bundle.flatMap(cmd.cancelableRun(_, rest))
        }
      case _ => bundle.map(_ => ())
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run(): Unit = {
        logger.info("Shutting down ...")
        app.cancel()
      }
    })
    Await.result(app, Duration.Inf)
  }

}
