package info.hupel.isabelle.tests

import java.util.concurrent.Executors

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import org.log4s._

import org.specs2.specification.AfterAll
import org.specs2.specification.core.Env

import monix.execution.{ExecutionModel, Scheduler, UncaughtExceptionReporter}

import io.rbricks.scalog.{Level, LoggingBackend}

import info.hupel.isabelle.{Platform, System}
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

trait BasicSetup {

  LoggingBackend.console("info.hupel" -> Level.Debug)

  lazy val duration = 30.seconds

  val specs2Env: Env
  implicit val ee = specs2Env.executionEnv
  import specs2Env.executionEnv.ec

  lazy implicit val scheduler: Scheduler = Scheduler(
    Executors.newSingleThreadScheduledExecutor(),
    ec,
    UncaughtExceptionReporter(ec.reportFailure),
    ExecutionModel.AlwaysAsyncExecution
  )

  lazy val version: Version.Stable =
    Option(java.lang.System.getenv("ISABELLE_VERSION")).orElse(
      specs2Env.arguments.commandLine.value("isabelle.version")
    ).map(Version.parse) match {
      case None | Some(Left(_)) => sys.error("no or invalid test version specified")
      case Some(Right(v: Version.Stable)) => v
      case _ => sys.error("unsupported test version")
    }

  lazy val platform: Platform = Platform.guess.get
  lazy val setup: Setup = Setup.detect(platform, version, false).right.get

}

trait DefaultSetup extends BasicSetup {

  lazy val resources: Resources = Resources.dumpIsabelleResources().right.get
  lazy val isabelleEnv: Future[Environment] = setup.makeEnvironment(resources, Nil)

}

trait FullSetup extends DefaultSetup with AfterAll {

  import specs2Env.executionEnv.ec

  def session: String = "Protocol"

  lazy val config: Configuration = Configuration.simple(session)
  lazy val system: Future[System] = isabelleEnv.flatMap(System.create(_, config))


  val logger = getLogger

  def afterAll() = {
    logger.info("Shutting down system ...")
    Await.result(system.flatMap(_.dispose), duration)
  }

}
