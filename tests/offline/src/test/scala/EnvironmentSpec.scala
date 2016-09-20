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
    can be instantiated twice with the same path          ${same must exist.awaitFor(duration)}
    cannot be instantiated with a different path          ${diff must throwAn[Exception].awaitFor(duration)}"""


  // FIXME code duplication

  val context = Thread.currentThread.getContextClassLoader
  val constructor = Resolver.Default.resolve(platform, version).map { paths =>
    val clazz = new URLClassLoader(paths.map(_.toUri.toURL).toArray, context).loadClass(s"${Setup.defaultPackageName}.Environment")
    val constructor = clazz.getDeclaredConstructor(classOf[Environment.Context])
    constructor.setAccessible(true)
    constructor.asInstanceOf[Constructor[Environment]]
  }

  val user = Files.createTempDirectory("libisabelle_user")

  def instantiate(home: Path): Future[Environment] = constructor.map(_.newInstance(Environment.Context(home, user)))

  val first: Future[Environment] = instantiate(platform.setupStorage(version))

  val settingsPrefix = first.map { env =>
    val prefix = env.isabellePath(user.toAbsolutePath.toString)
    List("ISABELLE_BROWSER_INFO", "ISABELLE_OUTPUT", "ISABELLE_HOME_USER").forall { setting =>
      env.isabelleSetting(setting).startsWith(prefix)
    }
  }

  val same = first.flatMap { _ => instantiate(platform.setupStorage(version)) }
  val diff = first.flatMap { _ => instantiate(platform.setupStorage(version).resolve(".")) }

}
