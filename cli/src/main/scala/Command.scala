package edu.tum.cs.isabelle.cli

import scala.concurrent._

import org.log4s._

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup.Setup

import acyclic.file

/**
 * Simple bundle of an Isabelle [[edu.tum.cs.isabelle.setup.Setup setup]], a
 * matching [[edu.tum.cs.isabelle.api.Environment environment]] and a requested
 * [[edu.tum.cs.isabelle.api.Configuration configuration]].
 */
final case class Bundle(env: Environment, setup: Setup, configuration: Configuration)

trait Command {

  protected val logger = getLogger(getClass)

  def run(bundle: Bundle, args: List[String])(implicit ec: ExecutionContext): Future[Unit]

}
