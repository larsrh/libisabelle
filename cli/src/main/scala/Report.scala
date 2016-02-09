package edu.tum.cs.isabelle.cli

import java.nio.file.{Path, Paths}

import scala.concurrent._

import edu.tum.cs.isabelle._
import edu.tum.cs.isabelle.api._

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

    var reports = Reports.empty

    def markup(tree: XML.Tree) = reports += tree
    def finish() = ()

    for {
      s <- System.create(bundle.env, bundle.configuration)
      _ <- s.invoke(Operation.UseThys(markup, finish))(files)
      _ <- s.dispose
    }
    yield print(reports, bundle.env.home, format)
  }

}
