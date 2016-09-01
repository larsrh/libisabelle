package info.hupel.isabelle.cli

import scala.concurrent._

import info.hupel.isabelle.System

object Check extends Command {

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] =
    System.create(bundle.env, bundle.configuration).flatMap { sys =>
      logger.info("Alive!")
      sys.dispose
    }

}
