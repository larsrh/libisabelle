package edu.tum.cs.isabelle.api

import java.nio.file.Path

import scala.concurrent.ExecutionContext

abstract class Environment private[isabelle](home: Path) {

  val version =
    getClass().getAnnotation(classOf[Implementation]).version

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
      s"session $session" + path.map(p => s" at $p").getOrElse("") + s" for $toString"
  }


  private[isabelle] def build(config: Configuration): Int

  private[isabelle] val functionMarkup: String
  private[isabelle] val protocolMarkup: String
  private[isabelle] val initMarkup: String
  private[isabelle] val exitMarkup: String

  private[isabelle] type Session

  private[isabelle] def create(config: Configuration, consumer: (Markup, XMLBody) => Unit): Session
  private[isabelle] def sendOptions(session: Session): Unit
  private[isabelle] def sendCommand(session: Session, name: String, args: List[String]): Unit
  private[isabelle] def dispose(session: Session): Unit

  implicit def executionContext: ExecutionContext

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
