package edu.tum.cs.isabelle.cli

import java.nio.file.{Path, Paths}

import scala.concurrent._

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._

object Report extends Command {

  def UseThysMarkup(home: Path) = new Operation[List[String], Unit]("use_thys") {
    def prepare(args: List[String]): (XML.Tree, Observer[Unit]) = {
      val tree = Codec[List[String]].encode(args)
      println(s"<?xml version='1.0' ?>\n<dump home='$home'>\n")
      lazy val observer: Observer[Unit] = Observer.More(msg => {
        println(msg.pretty)
        observer
      }, _ => {
        println("\n</dump>")
        Observer.Success(ProverResult.Success(()))
      })

      (tree, observer)
    }
  }

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    for {
      s <- System.create(bundle.env, bundle.configuration)
      _ <- s.invoke(UseThysMarkup(bundle.env.home))(args)
      _ <- s.dispose
    }
    yield ()
  }

}
