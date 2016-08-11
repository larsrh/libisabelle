package info.hupel.isabelle.tests

import java.net.URLClassLoader
import java.nio.file.{Path, Paths}

import scala.concurrent.duration._

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.setup.{Resolver, Setup}

class EnvironmentSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Environment handling

  A low-level environment
    can be instantiated twice with the same path          ${same must exist.awaitFor(30.seconds)}
    cannot be instantiated with a different path          ${diff must throwAn[Exception].awaitFor(30.seconds)}"""


  // FIXME code duplication

  val context = Thread.currentThread.getContextClassLoader
  val constructor = Resolver.Default.resolve(platform, version).map { paths =>
    val clazz = new URLClassLoader(paths.map(_.toUri.toURL).toArray, context).loadClass(s"${Setup.defaultPackageName}.Environment")
    val constructor = clazz.getDeclaredConstructor(classOf[Environment.Context])
    constructor.setAccessible(true)
    constructor
  }

  def instantiate(home: Path) = constructor.map(_.newInstance(Environment.Context(home, implicitly)))

  val first = instantiate(platform.setupStorage(version))

  val same =
    for {
      _ <- first
      env2 <- instantiate(platform.setupStorage(version))
    }
    yield env2

  val diff =
    for {
      _ <- first
      env2 <- instantiate(platform.setupStorage(version).resolve("."))
    }
    yield env2

}
