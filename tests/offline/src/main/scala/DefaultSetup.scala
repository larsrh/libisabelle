package info.hupel.isabelle.tests

import java.nio.file.Paths

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

import org.log4s._

import org.specs2.Specification
import org.specs2.specification.AfterAll
import org.specs2.specification.core.Env

import info.hupel.isabelle.System
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

trait BasicSetup {

  lazy val duration = 30.seconds

  val specs2Env: Env
  implicit val ee = specs2Env.executionEnv
  import specs2Env.executionEnv.ec

  lazy val version: Version =
    Option(java.lang.System.getenv("ISABELLE_VERSION")).orElse(
      specs2Env.arguments.commandLine.value("isabelle.version")
    ).map(Version.apply).get

  lazy val platform: Platform = Platform.guess.get
  lazy val setup: Setup = Setup.detect(platform, version).right.get

}

trait DefaultSetup extends BasicSetup with AfterAll {

  import specs2Env.executionEnv.ec

  lazy val isabelleEnv: Future[Environment] = setup.makeEnvironment
  lazy val resources: Resources = Resources.dumpIsabelleResources().right.get

  def session: String = "Protocol"

  lazy val config: Configuration = resources.makeConfiguration(Nil, session)
  lazy val system: Future[System] = isabelleEnv.flatMap(System.create(_, config))


  val logger = getLogger

  def afterAll() = {
    logger.info("Shutting down system ...")
    Await.result(system.flatMap(_.dispose), duration)
  }

}
