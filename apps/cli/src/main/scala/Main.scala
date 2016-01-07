package edu.tum.cs.isabelle.app.cli

import scala.concurrent.Future
import scala.concurrent.duration._
import scala.sys.process._

import edu.tum.cs.isabelle.app._
import edu.tum.cs.isabelle.setup.Platform

object Main extends Template {

  def duration = Duration.Inf

  def run(bundle: Bundle) = bundle.setup.platform match {
    case Platform.Linux | Platform.OSX =>
      Future {
        val logic = bundle.args.headOption.getOrElse("HOL")
        val binary = bundle.env.home.resolve("bin").resolve("isabelle")
        val nullLogger = ProcessLogger(_ => ())

        logger.info(s"Starting Isabelle/jEdit with logic $logic ...")

        Seq(binary.toString, "jedit", "-l", logic).run(nullLogger)
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
