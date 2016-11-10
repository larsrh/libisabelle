package info.hupel.isabelle.tests

import java.lang.reflect.Constructor
import java.net.URLClassLoader
import java.nio.file.{Files, Path, Paths}

import scala.concurrent.Future
import scala.concurrent.duration._

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup.{Resolver, Setup}

class EnvironmentSpec(val specs2Env: Env) extends Specification with BasicSetup with IsabelleMatchers { def is = s2"""

  Environment handling

  A low-level environment
    respects the USER_HOME setting                        ${settingsPrefix must beTrue.awaitFor(duration)}
    can be instantiated                                   ${first must exist.awaitFor(duration)}
    cannot be instantiated for a second time              ${second must throwAn[Exception].awaitFor(duration)}"""

  val classLoader = setup.makeClassLoader(Resolver.Default)
  val user = Files.createTempDirectory("libisabelle_user")
  val context = Environment.Context(setup.home, user)

  def instantiate = classLoader.map(Environment.instantiate(version, _, context))

  val first: Future[Environment] = instantiate

  val settingsPrefix = first.map { env =>
    val prefix = env.isabellePath(user.toAbsolutePath.toString)
    List("ISABELLE_BROWSER_INFO", "ISABELLE_OUTPUT", "ISABELLE_HOME_USER").forall { setting =>
      env.isabelleSetting(setting).startsWith(prefix)
    }
  }

  val second = first.flatMap { _ => instantiate }

}
