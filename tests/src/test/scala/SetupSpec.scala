package edu.tum.cs.isabelle.tests

import java.nio.file.Files

import org.specs2.Specification
import org.specs2.specification.core.Env

import cats.data.Xor

import edu.tum.cs.isabelle.setup._

class SetupSpec(val specs2Env: Env) extends Specification with DefaultSetup { def is = s2"""

  Isabelle setup

  Isabelle setup detection
    should handle absent setups      $absent
    should handle corrupted setups   $corrupted"""

  def absent = {
    val dir = Files.createTempDirectory("libisabelle_test")
    val platform = Platform.genericPlatform(dir)
    Files.createDirectories(platform.setupStorage)
    Setup.detectSetup(platform, version).toEither must beLeft(Setup.Absent)
  }

  def corrupted = {
    val dir = Files.createTempDirectory("libisabelle_test")
    val platform = Platform.genericPlatform(dir)
    Files.createDirectories(platform.setupStorage(version))
    Setup.detectSetup(platform, version).toEither must beLeft.like {
      case Setup.Corrupted(_) => true
    }
  }

}
