package edu.tum.cs.isabelle.api

import java.nio.file.Path

import scala.concurrent.ExecutionContext

/**
 * Result from the prover.
 *
 * In the error case, usually a special
 * [[edu.tum.cs.isabelle.Codec.ProverException ProverException]] will be
 * provided, though implementations of an [[Environment environment]] may
 * choose differently.
 *
 * @see [[edu.tum.cs.isabelle.System#invoke]]
 * @see [[edu.tum.cs.isabelle.Codec.exn]]
 */
trait ProverResult[+T]

object ProverResult {
  case class Success[+T](t: T) extends ProverResult[T]
  case class Failure(exn: Exception) extends ProverResult[Nothing]
}

object Environment {

  private[isabelle] def getVersion(clazz: Class[_ <: Environment]): Option[Version] =
    Option(clazz.getAnnotation(classOf[Implementation]).identifier).map(Version.apply)

}

abstract class Environment private[isabelle](home: Path) { self =>

  final val version = Environment.getVersion(getClass()).get

  override def toString: String =
    s"$version at $home"

  type XMLTree
  type XMLBody = List[XMLTree]

  def fromYXML(source: String): XMLTree
  def toYXML(tree: XMLTree): String

  def elem(markup: Markup, body: XMLBody): XMLTree
  def text(content: String): XMLTree

  def destTree(tree: XMLTree): Either[String, (Markup, XMLBody)]

  def foldTree[A](text: String => A, elem: (Markup, List[A]) => A)(tree: XMLTree): A =
    destTree(tree) match {
      case Left(content) => text(content)
      case Right((markup, body)) => elem(markup, body.map(foldTree(text, elem)))
    }


  object Configuration {
    def fromPath(path: Path, session: String) =
      Configuration(Some(path), session)
  }

  case class Configuration(path: Option[Path], session: String) {
    override def toString: String =
      s"session $session" + path.map(p => s" at $p").getOrElse("") + s" for $self"
  }


  protected[isabelle] def build(config: Configuration): Int

  protected[isabelle] val functionTag: String
  protected[isabelle] val protocolTag: String
  protected[isabelle] val initTag: String
  protected[isabelle] val exitTag: String

  protected[isabelle] type Session

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XMLBody) => Unit): Session
  protected[isabelle] def sendOptions(session: Session): Unit
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]): Unit
  protected[isabelle] def dispose(session: Session): Unit

  /**
   *
   * Implementations should ensure that the underlying thread pool consists of
   * daemon threads, rendering [[edu.tum.cs.isabelle.System#dispose disposing]]
   * of running systems unnecessary.
   */
  implicit val executionContext: ExecutionContext

  sealed trait Observer[T]
  object Observer {
    case class Success[T](t: ProverResult[T]) extends Observer[T]
    case class Failure[T](error: Exception) extends Observer[T]
    case class More[T](step: XMLTree => Observer[T], done: XMLTree => Observer[T]) extends Observer[T]

    def ignoreStep[T](done: XMLTree => Observer[T]): Observer[T] = {
      lazy val observer: Observer[T] = More(_ => observer, done)
      observer
    }

  }

}
