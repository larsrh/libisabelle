package edu.tum.cs.isabelle.api

import java.util.{Collections, WeakHashMap}
import java.nio.file.Path

import scala.concurrent.ExecutionContext

import org.log4s._
import shapeless.tag._

import acyclic.file

object Environment {

  private val logger = getLogger

  private[isabelle] def getVersion(clazz: Class[_ <: Environment]): Version =
    Option(clazz.getAnnotation(classOf[Implementation]).identifier) match {
      case None =>
        sys.error("malformed implementation")
      case Some(identifier) =>
        Version(identifier)
  }

  private val instances: java.util.Map[Class[_ <: Environment], Path] =
    Collections.synchronizedMap(new WeakHashMap())

  private def checkInstance(clazz: Class[_ <: Environment], home: Path): Unit = instances.synchronized {
    val version = getVersion(clazz)
    if (instances.containsKey(clazz)) {
      val oldHome = instances.get(clazz)
      if (instances.get(clazz) == home)
        logger.warn(s"Environment for $version has already been instantiated, but with the same path $oldHome")
      else {
        logger.error(s"Failed to instantiate environment for $version at $home; already existing at $oldHome")
        sys.error("invalid instantiation")
      }
    }
    else {
      logger.debug(s"Instantiating environment for $version at $home")
      instances.put(clazz, home)
      ()
    }
  }

  private[isabelle] def patchSettings(instance: Any, patch: Map[String, String]) = {
    val field = instance.getClass.getDeclaredField("_settings")
    field.setAccessible(true)
    val old = field.get(instance).asInstanceOf[Option[Map[String, String]]].get
    field.set(instance, Some(old ++ patch))
  }

  sealed trait Raw
  sealed trait Unicode

}


/**
 * Abstract interface for an Isabelle environment of a particular
 * [[Version version]] in a path with an underlying PIDE machinery.
 *
 * As opposed to a mere logic-less `[[edu.tum.cs.isabelle.setup.Setup Setup]]`,
 * an environment knows how to manage Isabelle processes. It can also manage
 * multiple running processes at the same time.
 *
 * A subclass of this class is called ''implementation'' throughout
 * `libisabelle`.
 *
 * It is highly recommended to use
 * [[edu.tum.cs.isabelle.setup.Setup#makeEnvironment]] to instantiate
 * implementations.
 *
 * While implementations may be created freely by users, it is recommended to
 * only use the bundled implementations for the supported Isabelle versions.
 * By convention, they live in the package `edu.tum.cs.isabelle.impl`. See also
 * `[[edu.tum.cs.isabelle.setup.Setup.defaultPackageName Setup.defaultPackageName]]`.
 *
 * ''Contract''
 *
 *   - An implementation is a subclass of this class.
 *   - The class name of the implementation must be `Environment`. There must
 *     be a `BuildInfo` class in the same package.
 *   - Implementations must be final and provide a constructor with exactly one
 *     argument (of type `java.nio.file.Path`). There must be no other
 *     constructors. The constructor should be `private`.
 *   - Implementations must be annotated with
 *     `[[edu.tum.cs.isabelle.api.Implementation Implementation]]`, where the
 *     given [[edu.tum.cs.isabelle.api.Implementation.identifier identifier]]
 *     corresponds to the [[Version version identifier]].
 *
 * ''Footnote''
 *
 * Due to name clashes in the underlying PIDE machinery (which is provided by
 * Isabelle itself and is not under control of `libisabelle`), it is impossible
 * to have multiple environments for different versions in the same class
 * loader. This is the primary reason why this class exists in the first place,
 * to enable seamless abstraction over multiple PIDEs.
 */
abstract class Environment protected(val home: Path) { self =>

  Environment.checkInstance(getClass(), home)

  final val version: Version = Environment.getVersion(getClass())

  final val variables: Map[String, String] = Map(
    "ISABELLE_VERSION" -> version.identifier,
    "LIBISABELLE_GIT" -> BuildInfo.gitHeadCommit.getOrElse(""),
    "LIBISABELLE_VERSION" -> BuildInfo.version
  )

  override def toString: String =
    s"$version at $home"


  protected final val logger = getLogger

  protected[isabelle] def build(config: Configuration): Int

  protected[isabelle] val functionTag: String
  protected[isabelle] val protocolTag: String
  protected[isabelle] val initTag: String
  protected[isabelle] val exitTag: String

  protected[isabelle] type Session

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XML.Body) => Unit): Session
  protected[isabelle] def sendOptions(session: Session): Unit
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]): Unit
  protected[isabelle] def dispose(session: Session): Unit

  def decode(text: String @@ Environment.Raw): String @@ Environment.Unicode
  def encode(text: String @@ Environment.Unicode): String @@ Environment.Raw

  /**
   * The [[scala.concurrent.ExecutionContext execution context]] internally
   * used by the underlying PIDE implementation.
   *
   * It is allowed to override the execution context of internal PIDE
   * implementation during initialization, but it must remain fixed afterwards.
   * This field must be set to that execution context.
   *
   * Implementations should ensure that the underlying thread pool consists of
   * daemon threads, rendering [[edu.tum.cs.isabelle.System#dispose disposing]]
   * of running systems unnecessary. (The secondary reason is to avoid a
   * hanging JVM when user code did not handle an exception, the main thread
   * gets terminated, but worker threads are keeping the JVM alive.)
   *
   * This is exposed to the user via
   * `[[edu.tum.cs.isabelle.System#executionContext System#executionContext]]`.
   */
  implicit val executionContext: ExecutionContext

}
