package info.hupel.isabelle.tests

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.api.Configuration

class SystemSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Isabelle system

  An Isabelle system
    detects startup failures   $failingSystem"""

  def create(session: String) =
    isabelleEnv.flatMap(System.create(_, Configuration.simple(session)))

  def check(session: String, reason: System.StartupException.Reason) =
    create(session).failed must be_==(System.StartupException(reason): Throwable).awaitFor(duration)

  val failingSystem = check("Unbuilt_Session", System.StartupException.Exited)

}
