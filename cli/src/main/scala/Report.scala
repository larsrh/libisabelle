package edu.tum.cs.isabelle.cli

import java.nio.file.{Path, Paths}

import scala.concurrent._

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._

object Report extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    def markup(tree: XML.Tree) = println(tree.pretty)
    def finish() = println("\n</dump>")

    for {
      s <- System.create(bundle.env, bundle.configuration)
      _ = println(s"<?xml version='1.0' ?>\n<dump home='${bundle.env.home}'>\n")
      _ <- s.invoke(Operation.UseThys(markup, finish))(args)
      _ <- s.dispose
    }
    yield ()
  }

}
