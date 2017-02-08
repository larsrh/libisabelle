package info.hupel.isabelle.cli

import java.net.URLClassLoader
import java.nio.file._

import org.apache.commons.io.FileUtils

import scala.collection.JavaConverters._
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

import caseapp._

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object Main {

  private lazy val logger = getLogger

  def main(args: Array[String]): Unit = Options.parse(args.toList) { (options, rest) =>
    val parentClassLoader =
      if (options.internal) getClass.getClassLoader else null

    val afp =
      if (options.afp)
        Set(Dependency(Module(s"${BuildInfo.organization}.afp", s"afp-${options.version.identifier}"), "1.0.+"))
      else
        Set()

    val classpath = options.fetch.traverseU(Parse.moduleVersion(_, BuildInfo.scalaBinaryVersion)) match {
      case Right(Nil) if !options.afp => Future.successful { Nil }
      case Right(modules) => Options.platform.fetchArtifacts(modules.map { case (mod, v) => Dependency(mod, v) }.toSet ++ afp)
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

    lazy val setup = options.home match {
      case None =>
        Setup.default(options.version) match {
          case Right(setup) => setup
          case Left(reason) => sys.error(reason.explain)
        }
      case Some(home) =>
        Setup(home, Options.platform, options.version)
    }

    logger.info(s"Using ${options.configuration}")
    lazy val bundle = for {
      cs <- CancelableFuture(components, Cancelable.empty)
      env <- setup.makeEnvironment(Resolver.Default, options.userPath, cs)
    } yield Bundle(env, setup, options.configuration)

    val app = rest match {
      case cmd :: rest =>
        Options.commands.get(cmd) match {
          case None =>
            Options.usageAndExit(s"no such command `$cmd`")
          case Some(cmd) =>
            bundle.flatMapC(cmd.cancelableRun(_, rest))
        }
      case _ => bundle.map(_ => ())
    }

    Runtime.getRuntime().addShutdownHook(new Thread() {
      override def run(): Unit =
        logger.info("Shutting down ...")
        app.cancel()
    })
    Await.result(app, Duration.Inf)
  }

}
