package edu.tum.cs.isabelle

import java.net.URLClassLoader
import java.nio.file.Path

import scala.util.control.Exception._

import edu.tum.cs.isabelle.api._

object Implementations extends App {
  def empty: Implementations = new Implementations(Map.empty)

  case class Entry(paths: List[Path], name: String)
}

class Implementations private(entries: Map[Version, Implementations.Entry]) {

  private def loadClass(entry: Implementations.Entry): Option[Class[_ <: Environment]] = {
    val classLoader = new URLClassLoader(entry.paths.map(_.toUri.toURL).toArray, Thread.currentThread.getContextClassLoader)
    catching(classOf[ClassCastException]) opt classLoader.loadClass(entry.name).asSubclass(classOf[Environment])
  }

  def add(entry: Implementations.Entry): Option[Implementations] =
    loadClass(entry).flatMap(Environment.getVersion) map { version =>
      new Implementations(entries + (version -> entry))
    }

  def addAll(entries: List[Implementations.Entry]): Option[Implementations] =
    entries.foldLeft(Some(this): Option[Implementations]) { (impls, entry) =>
      impls.flatMap(_.add(entry))
    }

  def versions: Set[Version] = entries.keySet

  def makeEnvironment(home: Path, version: Version) =
    entries get version flatMap loadClass map { clazz =>
      clazz.getConstructor(classOf[Path]).newInstance(home)
    }


}
