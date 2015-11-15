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
    def prepare(env: Environment, args: List[String]): (env.XMLTree, env.Observer[Unit]) = {
      val tree = Codec[List[String]].encode(env)(args)
      lazy val observer: env.Observer[Unit] = env.Observer.More(msg => {
        println(msg)
        observer
      }, _ => env.Observer.Success(ProverResult.Success(())))

      (tree, observer)
    }
  }

  def run(bundle: Bundle) =
    for {
      s <- bundle.system
      _ <- s.invoke(UseThysMarkup)(bundle.args)
      _ <- s.dispose
    }
    yield ()

}
