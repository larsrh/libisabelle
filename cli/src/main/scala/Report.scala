package info.hupel.isabelle.cli

import java.nio.file.{Path, Paths}

import scala.concurrent._

import info.hupel.isabelle._
import info.hupel.isabelle.api._

object Report extends Command {

  sealed abstract class Format
  case object RawXML extends Format
  case object XRay extends Format

  def print(reports: Reports, home: Path, format: Format): Unit = format match {
    case RawXML =>
      println(s"<?xml version='1.0' ?>\n<dump home='$home'>\n")
      reports.items.foreach(tree => println(tree.pretty(2)))
      println("\n</dump>")
    case XRay =>
      println(reports.interpret(home).pretty)
  }

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit] = {
    val (format, files) = args match {
      case "--format" :: "raw-xml" :: files => (RawXML, files)
      case "--format" :: "x-ray" :: files => (XRay, files)
      case "--format" :: _ => sys.error("unknown format")
      case _ => (RawXML, args)
    }

    for {
      s <- System.create(bundle.env, bundle.configuration)
      reports <- s.invoke(Operation.UseThys(Reports.empty)(_ + _, identity))(files)
      _ <- s.dispose
    }
    yield print(reports.unsafeGet, bundle.env.home, format)
  }

}
