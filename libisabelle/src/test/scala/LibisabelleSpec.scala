package edu.tum.cs.isabelle

import scala.concurrent.duration._

import isabelle.XML

import org.specs2.Specification
import org.specs2.matcher.Matcher
import org.specs2.time.NoTimeConversions

class LibisabelleSpec extends Specification with NoTimeConversions { def is = s2"""

  This is a specification to test basic protocol interaction

  An Isabelle session
    can be started        $start
    reacts to requests    $req
    can be torn down      $stop"""


  val system = System.instance(Some(new java.io.File(".")), "Protocol")
  val response = system.flatMap(_.invoke(Operation.Hello)("world"))
  val teardown = for { s <- system; _ <- response /* wait for response */; _ <- s.dispose } yield ()

  def exist[A]: Matcher[A] = ((a: A) => a != null, "doesn't exist")


  def start = system must exist.await(timeout = 10.seconds)
  def req = response must beRight("Hello world").await(timeout = 5.seconds)
  def stop = teardown must exist.await(timeout = 5.seconds)

}
