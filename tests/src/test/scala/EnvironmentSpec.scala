package edu.tum.cs.isabelle.tests

import java.net.URLClassLoader
import java.nio.file.{Path, Paths}

import scala.concurrent.duration._

import org.specs2.Specification
import org.specs2.specification.core.Env

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup.Setup

class EnvironmentSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Environment handling

  A low-level environment
    can be instantiated twice with the same path          ${same must exist.awaitFor(30.seconds)}
    cannot be instantiated with a different path          ${diff must throwAn[Exception].awaitFor(30.seconds)}"""


  // FIXME code duplication

  val context = Thread.currentThread.getContextClassLoader
  val classLoader = Setup.fetchImplementation(platform, version).map { paths =>
    new URLClassLoader(paths.map(_.toUri.toURL).toArray, context)
  }

  def instantiate(home: Path) =
    classLoader.map(_.loadClass("edu.tum.cs.isabelle.impl.Environment").getDeclaredConstructor(classOf[Path]).newInstance(home))

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
