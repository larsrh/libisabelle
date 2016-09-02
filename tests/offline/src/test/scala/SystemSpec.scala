package info.hupel.isabelle.tests

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.Specification
import org.specs2.specification.core.Env

import info.hupel.isabelle._
import info.hupel.isabelle.api._
import info.hupel.isabelle.pure._
import info.hupel.isabelle.hol._

class SystemSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Isabelle system

  An Isabelle system
    detects startup failures   ${failingSystem.failed must beAnInstanceOf[System.StartupException].awaitFor(duration)}"""

  val failingSystem =
    isabelleEnv.flatMap(System.create(_, resources.makeConfiguration(Nil, "Unbuilt_Session")))

}
