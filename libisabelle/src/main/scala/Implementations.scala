package edu.tum.cs.isabelle

import java.net.{URL, URLClassLoader}
import java.nio.file.Path

import scala.util.control.Exception._

import edu.tum.cs.isabelle.api._

import acyclic.file

object Implementations {

  /**
   * An empty managed [[Implementations set of implementations]].
   *
   * New implementations can be discovered via `[[Implementations#add]]` and
   * `[[Implementations#addAll]]`.
   */
  def empty: Implementations = new Implementations(Map.empty)

  /**
   * Class path coupled with a package name.
   *
   * It is expected that the specified class extends
   * `[[edu.tum.cs.isabelle.api.Environment Environment]]`. Furthermore, the
   * class path should contain all required dependencies.
   */
  case class Entry(urls: List[URL], packageName: String)

  /**
   * Construct a [[edu.tum.cs.isabelle.api.Environment environment]] in the
   * class loader from an already loaded class.
   *
   * This method should not be used directly. Rather, users should go through
   * the higher-level `[[Implementations#makeEnvironment makeEnvironment]]`.
   * (See that method for more details.)
   */
  def makeEnvironment(home: Path, clazz: Class[_ <: Environment]): Environment =
    clazz.getConstructor(classOf[Path]).newInstance(home)

}

/**
 * A managed set of known implementations of
 * `[[edu.tum.cs.isabelle.api.Environment Environment]]`.
 *
 * An empty instance can be obtained via `[[Implementations.empty]]`.
 */
class Implementations private(entries: Map[Version, Implementations.Entry]) {

  // FIXME error handling

  private def loadClass(entry: Implementations.Entry): Class[_ <: Environment] = {
    val classLoader = new URLClassLoader(entry.urls.toArray, getClass.getClassLoader)
    val env = classLoader.loadClass(s"${entry.packageName}.Environment").asSubclass(classOf[Environment])
    val info = classLoader.loadClass(s"${entry.packageName}.BuildInfo").getDeclaredMethod("toString").invoke(null)
    if (BuildInfo.toString != info.toString)
      sys.error(s"build info does not match")
    env
  }

  /**
   * Load a class from a class path in a fresh class loader, detect the
   * [[edu.tum.cs.isabelle.api.Version version]] it is implementing, and, if
   * available, add it to the known implementations.
   *
   * The class loader and the loaded class are discarded immediately
   * afterwards.
   */
  def add(entry: Implementations.Entry): Implementations = {
    val version = Environment.getVersion(loadClass(entry))
    new Implementations(entries + (version -> entry))
  }

  /**
   * Load a series of classes.
   *
   * @see [[add]]
   */
  def addAll(entries: List[Implementations.Entry]): Implementations =
    entries.foldLeft(this)(_ add _)

  /** Known versions. */
  def versions: Set[Version] = entries.keySet

  /**
   * Construct a [[edu.tum.cs.isabelle.api.Environment environment]] in a fresh
   * class loader, if the specified version is known.
   *
   * Environments created through this method are unique and independent,
   * because they reside in different class loaders.
   *
   * Users may want to go call this method via
   * [[edu.tum.cs.isabelle.setup.Setup#makeEnvironment Setup.makeEnvironment]],
   * which gives stronger guarantees about the success of this method.
   *
   * ''Footnote''
   *
   * Advanced class loading trickery is needed to manage multiple environments
   * stemming from different [[edu.tum.cs.isabelle.setup.Setup setups]].
   * `libisabelle` itself is a more-or-less thin wrapper around the actual
   * Isabelle interface ("PIDE"), which relies on global state for environment
   * variables such as the home path. Once set, they cannot be changed.
   * However, on the JVM, global state is scoped to the class loader, i.e.
   * multiple "global states" may coexist in the same virtual machine. This is
   * exactly the trick used here.
   */
  def makeEnvironment(home: Path, version: Version): Option[Environment] =
    entries get version map loadClass map { Implementations.makeEnvironment(home, _) }

}
