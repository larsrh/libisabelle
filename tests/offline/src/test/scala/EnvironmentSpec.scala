package info.hupel.isabelle.tests

import java.nio.file.Files

import scala.util.control.NonFatal

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup.Resolver

class EnvironmentSpec(val specs2Env: Env) extends Specification with BasicSetup with IsabelleMatchers { def is = s2"""

  Environment handling

  A low-level environment
    respects the USER_HOME setting                        ${settingsPrefix must beTrue.awaitFor(duration)}
    cleans up etc/components after failure                ${cleanedUp must beTrue.awaitFor(duration)}"""

  val classpath = Resolver.Default.resolve(platform, version)
  val user = Files.createTempDirectory("libisabelle_user")
  val context = Environment.Context(setup.home, user, Nil, Nil)

  val settingsPrefix = classpath.map { paths =>
    val env = Environment.instantiate(version, paths, context)
    val prefix = env.isabellePath(user.toAbsolutePath.toString)
    List("ISABELLE_BROWSER_INFO", "ISABELLE_OUTPUT", "ISABELLE_HOME_USER").forall { setting =>
      env.isabelleSetting(setting).startsWith(prefix)
    }
  }

  val cleanedUp = classpath.map { paths =>
    try {
      val testContext = context.copy(
        home = Files.createTempDirectory("nonexistent_home"),
        components = List(Files.createTempDirectory("dummy_component"))
      )
      Environment.instantiate(version, paths, testContext)
    }
    catch {
      case NonFatal(ex) =>
    }

    !Files.exists(context.etcComponents(version))
  }

}
