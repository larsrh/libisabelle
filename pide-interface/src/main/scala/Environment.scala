package edu.tum.cs.isabelle.api

import java.nio.file.Path

import scala.concurrent.ExecutionContext

/**
 * Result from the prover.
 *
 * In the error case, a special
 * [[edu.tum.cs.isabelle.Operation.ProverException ProverException]] will be
 * provided.
 *
 * @see [[edu.tum.cs.isabelle.System#invoke]]
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
 * class serves as a registry of those, although using it is not required.
 *
 * Users may instantiate implementations manually, although there is a caveat:
 * After one implementation has been instantiated, the behaviour of subsequent
 * instantiations with a different path or instantiations of a different
 * implementation is undefined. For most applications, this is not a
 * significant restriction, because they only deal with a single setup.
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
 *   - Implementations must be final and provide a public constructor with
 *     exactly one argument (of type `java.nio.file.Path`).
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

  /** Convenience constructors for [[Configuration configurations]]. */
  object Configuration {
    /**
     * Creates a [[Configuration configuration]] from an additional path, that
     * is, the specified session is declared in a `ROOT` file in that path
     * (or indirectly via a `ROOTS` file).
     *
     * In almost all cases, it should refer to a session which has one of the
     * `Protocol` session of the
     * [[https://github.com/larsrh/libisabelle-protocol accompanying theory sources]]
     * as an ancestor, or includes these theories in some other way.
     *
     * The given path must not be identical to or be a subdirectory of the
     * Isabelle home path. It must also, either directly or indirectly via a
     * `ROOTS` file, contain declarations for all ancestor sessions.
     */
    def fromPath(path: Path, session: String) =
      Configuration(Some(path), session)

    /**
     * Creates a [[Configuration configuration]] with an empty path, that is,
     * it must be a session included in the Isabelle distribution.
     *
     * Unless using a custom Isabelle distribution, a
     * [[edu.tum.cs.isabelle.System.create system created]] with such a
     * configuration will be unable to reply to any
     * [[edu.tum.cs.isabelle.Operation operation]].
     */
    def fromBuiltin(session: String) =
      Configuration(None, session)
  }

  /**
   * Represents the location and name of a ''session'' (Isabelle terminology).
   *
   * Refer to the Isabelle system manual for details about sessions.
   * `libisabelle` assumes that users are familiar with the session handling
   * of Isabelle.
   *
   * Creation of configurations is completely unchecked. Errors such as
   * non-existing paths will only manifest themselves when attempting to
   * [[edu.tum.cs.isabelle.System.build build]] a configuration or
   * [[edu.tum.cs.isabelle.System.create create]] a
   * [[edu.tum.cs.isabelle.System system]]. Nonetheless, users should go
   * through one of the constructors in the
   * [[Configuration$ companion object]].
   */
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

  /**
   * An iteratee-like structure consuming [[XMLTree XML trees]] and eventually
   * producing an output of type `T`, or an error.
   *
   * On a high level, this can be imagined like a function taking a list of
   * trees as an argument. In most cases, user code does not care about the
   * intermediate results. For that, combinators exist in the
   * [[Observer$ companion object]] and
   * `[[edu.tum.cs.isabelle.Operation$ Operation]]`.
   *
   * @see [[edu.tum.cs.isabelle.System#invoke]]
   * @see [[edu.tum.cs.isabelle.Operation]]
   */
  sealed abstract class Observer[T]

  /** Cases of [[Observer observers]] and combinators. */
  object Observer {
    /**
     * Represents a final result from the prover, be it successful or failed.
     *
     * It is recommended that this case is not used for unexpected failures.
     *
     * For the precise error semantics, see
     * `[[edu.tum.cs.isabelle.System#invoke System#invoke]]`.
     */
    case class Success[T](t: ProverResult[T]) extends Observer[T]

    /**
     * Represents an unexpected failure during communication with the prover.
     *
     * It is recommended that this case is not used for routine error
     * conditions.
     *
     * For the precise error semantics, see
     * `[[edu.tum.cs.isabelle.System#invoke System#invoke]]`.
     */
    case class Failure[T](error: Exception) extends Observer[T]

    /**
     * An [[Observer observer]] waiting for more intermediate data (`step`) or
     * a final result (`done`).
     *
     * When receiving a final result, the returned new observer should be
     * either `[[Success]]` or `[[Failure]]`. This is not enforced, but not
     * doing so will most likely result in hanging
     * [[edu.tum.cs.isabelle.Operation operations]].
     */
    case class More[T](step: XMLTree => Observer[T], done: XMLTree => Observer[T]) extends Observer[T]

    /**
     * Combinator for producing an [[Observer observer]] which ignores
     * intermediate data.
     */
    def ignoreStep[T](done: XMLTree => Observer[T]): Observer[T] = {
      lazy val observer: Observer[T] = More(_ => observer, done)
      observer
    }

  }

}
