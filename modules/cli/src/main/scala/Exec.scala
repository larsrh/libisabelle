package info.hupel.isabelle.cli

import scala.concurrent._
import scala.sys.process._

import info.hupel.isabelle.setup.Platform

object Exec extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = bundle.setup.platform match {
    case Platform.Linux | Platform.OSX =>
      Future {
        val binary = bundle.env.home.resolve("bin").resolve("isabelle")

        val cli = binary.toString :: args

        logger.info(s"Starting Isabelle with arguments ${args.mkString(" ")} ...")
        logger.debug(s"Executing ${cli.mkString(" ")}")

        Process(cli, None, bundle.env.settings.toList: _*).run(connectInput = true).exitValue()

        logger.info("Done.")
        ()
      }
    case Platform.Windows =>
      logger.error(s"Isabelle can't be started under Windows")
      sys.error("unsupported")
    case _ =>
      logger.error(s"Isabelle can't be started under unofficial platforms")
      sys.error("unsupported")
  }

}
