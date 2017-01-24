package info.hupel.isabelle.cli

import scala.concurrent._

object Exec extends Command {

  override def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = args match {
    case tool :: args =>
      Future.successful {
        logger.info(s"Starting Isabelle tool $tool with arguments ${args.mkString(" ")} ...")
        val rc = bundle.env.exec(tool, args)
        logger.info(s"Exited with status $rc")
      }
    case Nil =>
      sys.error("missing parameters for 'exec'")
  }

}
