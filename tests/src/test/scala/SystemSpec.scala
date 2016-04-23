package edu.tum.cs.isabelle.tests

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration._
import scala.math.BigInt

import org.specs2.Specification
import org.specs2.specification.core.Env

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.pure._
import edu.tum.cs.isabelle.hol._

class SystemSpec(val specs2Env: Env) extends Specification with DefaultSetup with IsabelleMatchers { def is = s2"""

  Isabelle system

  An Isabelle system
    detects startup failures   ${failingSystem.failed must beAnInstanceOf[System.StartupException].awaitFor(timeout)}"""


  def timeout = 10.seconds

  val failingSystem = env.flatMap(System.create(_, resources.makeConfiguration(Nil, "Unbuilt_Session")))

}
