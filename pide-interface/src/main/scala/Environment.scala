package edu.tum.cs.isabelle.api

import java.util.{Collections, WeakHashMap}
import java.nio.file.Path

import scala.concurrent.ExecutionContext

import org.log4s._

import acyclic.file


object Environment {

  private val logger = getLogger

  private[isabelle] def getVersion(clazz: Class[_ <: Environment]): Version = {
    val identifier = clazz.getAnnotation(classOf[Implementation]).identifier
    if (identifier == null)
      sys.error("malformed implementation")
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
      logger.info(s"Instantiating environment for $version at $home")
      instances.put(clazz, home)
      ()
    }
  }

}


/**
 * Abstract interface for an Isabelle environment of a particular
 * [[Version version]] in a path with an underlying PIDE machinery.
 *
 * As opposed to a mere logic-less `[[edu.tum.cs.isabelle.setup.Setup Setup]]`,
 * an environment knows how to manage Isabelle processes. It can also manage
 * multiple running processes at the same time.
 *
 * A subclass of this class is called ''implementation'' througout
 * `libisabelle`. The `[[edu.tum.cs.isabelle.Implementations Implementations]]`
 * class serves as a registry of those and using it is strongly recommended.
 * (Since subclasses should `protect` their constructors, manual instantiation
 * would not work anyway.)
 *
 * For multi-home or multi-version scenarios, it is highly recommended that
 * users create environments through
 * [[edu.tum.cs.isabelle.Implementations#makeEnvironment the appropriate function]]
 * of a registry. See its documentation for an explanation.
 *
 * If in doubt, users should prefer the direct (manual) instantiation.
 *
 * While implementations may be created freely by users, it is recommended to
 * only use the bundled implementations for the supported Isabelle versions.
 * By convention, they live in the package `edu.tum.cs.isabelle.impl` and their
 * class name is also `Environment`.
 *
 * ''Contract''
 *
 *   - An implementation is a subclass of this class.
 *   - Implementations must be final and provide a constructor with exactly one
 *    argument (of type `java.nio.file.Path`). There must be no other
 *    constructors. The constructor should be `protected`, but must be
 *    accessible from any class in the [[edu.tum.cs.isabelle isabelle]]
 *    package.
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
 *
 * As the caveat above states, not even multi-home scenarios are supported
 * without going through a registry. The user has to ensure that this happens,
 * since this class does not attempt to detect such a situation. While in
 * principle it could do so, it would require the introduction of even more
 * global mutable state. It might do so in the future.
 */
abstract class Environment protected[isabelle](home: Path) { self =>

  Environment.checkInstance(getClass(), home)

  final val version = Environment.getVersion(getClass())

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
