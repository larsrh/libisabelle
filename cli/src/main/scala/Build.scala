package edu.tum.cs.isabelle.cli

import scala.concurrent._

import edu.tum.cs.isabelle.System

object Build extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val built = System.build(bundle.env, bundle.configuration)
    if (!built)
      sys.error("build error")
    Future.successful { () }
  }

}
