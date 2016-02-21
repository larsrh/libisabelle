package edu.tum.cs.isabelle.cli

import scala.concurrent._
import scala.sys.process._

import edu.tum.cs.isabelle.setup.Platform

object JEdit extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = bundle.setup.platform match {
    case Platform.Linux | Platform.OSX =>
      Future {
        val binary = bundle.env.home.resolve("bin").resolve("isabelle")
        val nullLogger = ProcessLogger(_ => ())
        val logic = bundle.configuration.session
        val dirs = bundle.configuration.paths.flatMap(p => List("-d", p.toString))

        logger.info(s"Starting Isabelle/jEdit with logic $logic ...")

        Process(List(binary.toString, "jedit", "-l", logic) ::: dirs, None, bundle.env.variables.toList: _*).run(nullLogger)
        ()
      }
    case Platform.Windows =>
      logger.error(s"Isabelle/jEdit can't be started under Windows")
      sys.error("unsupported")
    case _ =>
      logger.error(s"Isabelle/jEdit can't be started under unofficial platforms")
      sys.error("unsupported")
  }

}
