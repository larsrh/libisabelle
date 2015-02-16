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


  val system = System.instance(Some(new java.io.File(".")), "Hello_PIDE")
  val response = system.flatMap(_.sendCommand("hello", XML.Encode.string("world"))).map(XML.Decode.string)
  val response = system.flatMap(_.sendCommand("Iterator", XML.Encode.int(1))).map(XML.Decode.int)
  val teardown = for { s <- system; _ <- response /* wait for response */; _ <- s.dispose() } yield ()

  def exist[A]: Matcher[A] = ((a: A) => a != null, "doesn't exist")


  def start = system must exist.await(timeout = 10.seconds)
  def req = response must be_===("Hello world").await(timeout = 5.seconds)
  def stop = teardown must exist.await(timeout = 5.seconds)

}
