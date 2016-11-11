package info.hupel.isabelle.cli

import scala.concurrent._
import scala.sys.process._

import info.hupel.isabelle.setup.Platform

object JEdit extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = Future.successful {
    val logic = bundle.configuration.session
    val dirs = bundle.configuration.paths.flatMap(p => List("-d", p.toString))

    val cli = List("-l", logic) ::: dirs ::: args
    val rc = bundle.env.exec("jedit", cli)
    logger.info(s"Exited with status $rc")
  }

}
