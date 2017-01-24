package info.hupel.isabelle.cli

import scala.concurrent._

import info.hupel.isabelle._
import info.hupel.isabelle.api._

object Report extends Command {

  sealed abstract class Format
  case object RawXML extends Format
  case object Model extends Format
  case object XRay extends Format

  def print(reports: Reports, env: Environment, format: Format): Unit = format match {
    case RawXML =>
      println(s"<?xml version='1.0' ?>\n<dump home='${env.home}'>\n")
      reports.items.foreach(tree => println(tree.pretty(2)))
      println("\n</dump>")
    case Model =>
      val model = reports.interpret(env)

      println(model.pretty)
    case XRay =>
      val model = reports.interpret(env)

      println("<!DOCTYPE html>")
      println(model.toHTML)
  }

  override def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val (format, files) = args match {
      case "--format" :: "raw-xml" :: files => (RawXML, files)
      case "--format" :: "model" :: files => (Model, files)
      case "--format" :: "x-ray" :: files => (XRay, files)
      case "--format" :: _ => sys.error("unknown format")
      case _ => (RawXML, args)
    }

    for {
      s <- System.create(bundle.env, bundle.configuration)
      r <- s.invoke(Operation.UseThys(Reports.empty)(_ + _, identity))(files)
      _ <- s.dispose
    }
    yield print(r.unsafeGet, bundle.env, format)
  }

}
