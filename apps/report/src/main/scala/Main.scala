package edu.tum.cs.isabelle.app.report

import java.nio.file.Paths

import scala.concurrent._
import scala.concurrent.duration._

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.app._

object Main extends Template {

  def duration = Duration.Inf

  val UseThysMarkup = new Operation[List[String], Unit]("use_thys") {
    def prepare(args: List[String]): (XML.Tree, Observer[Unit]) = {
      val tree = Codec[List[String]].encode(args)
      println("<?xml version='1.0' ?>\n<dump>\n")
      lazy val observer: Observer[Unit] = Observer.More(msg => {
        println(msg.pretty())
        observer
      }, _ => {
        println("\n</dump>")
        Observer.Success(ProverResult.Success(()))
      })

      (tree, observer)
    }
  }

  def run(bundle: Bundle) = {
    val config = Configuration.fromPath(Paths.get("."), s"HOL-Protocol${bundle.version.identifier}")
    val built = System.build(bundle.env, config)
    if (!built)
      sys.error("build error")

    for {
      s <- System.create(bundle.env, config)
      _ <- s.invoke(UseThysMarkup)(bundle.args)
      _ <- s.dispose
    }
    yield ()
  }

}
