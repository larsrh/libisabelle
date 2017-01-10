package info.hupel.isabelle.cli

import scala.concurrent._

import info.hupel.isabelle.System

object Build extends Command {

  override def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val built = System.build(bundle.env, bundle.configuration)
    if (!built)
      sys.error("build error")
    Future.successful { () }
  }

}
