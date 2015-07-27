package edu.tum.cs.isabelle

import java.nio.file.Paths

import scala.concurrent.duration._

import isabelle.Exn

import org.specs2.Specification
import org.specs2.concurrent.ExecutionEnv
import org.specs2.matcher.Matcher

import edu.tum.cs.isabelle.setup.{Configuration, Setup}

class LibisabelleSpec(implicit env: ExecutionEnv) extends Specification with IsabelleMatchers { def is = s2"""

  Basic protocol interaction

  An Isabelle session
    can be started          ${system must exist.awaitFor(30.seconds)}
    can load theories       ${loaded must beRes(()).awaitFor(30.seconds)}
    reacts to requests      ${response must beRes("prop => prop => prop").awaitFor(30.seconds)}
    can be torn down        ${teardown must exist.awaitFor(30.seconds)}"""


  val TypeOf = Operation.implicitly[String, String]("type_of")

  val environment = Setup.guessEnvironment(Setup.defaultBasePath).get
  val config = Configuration.fromPath(Paths.get("."), "Protocol")

  val system = System.instance(environment, config)
  val loaded = system.flatMap(_.invoke(Operation.UseThys)(List("libisabelle/src/test/isabelle/Test")))
  val response = for { s <- system; _ <- loaded; res <- s.invoke(TypeOf)("op ==>") } yield res
  val teardown = for { s <- system; _ <- response /* wait for response */; _ <- s.dispose } yield ()

  def exist[A]: Matcher[A] = ((a: A) => a != null, "doesn't exist")

}
