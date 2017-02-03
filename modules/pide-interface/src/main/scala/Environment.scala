package info.hupel.isabelle.api

import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file._

import monix.execution.Scheduler

import org.log4s._

import shapeless.tag._

case class ExitTrap(rc: Int) extends RuntimeException

object Environment {

  private val logger = getLogger

  private def getVersion(clazz: Class[_ <: Environment]): Version =
    Option(clazz.getAnnotation(classOf[Implementation]).identifier) match {
      case None =>
        sys.error("malformed implementation")
      case Some(identifier) =>
        Version(identifier)
  }

  val packageName: String = "info.hupel.isabelle.impl"

  def instantiate(version: Version, classpath: List[Path], context: Context) = {
    logger.debug(s"Creating environment with classpath ${classpath.mkString(":")} ...")
    val classLoader = new URLClassLoader(classpath.map(_.toUri.toURL).toArray, getClass.getClassLoader)
    val env = classLoader.loadClass(s"$packageName.Environment").asSubclass(classOf[Environment])

    val actualVersion = getVersion(env)
    if (actualVersion != version)
      sys.error(s"expected version $version, got version $actualVersion")

    val info = classLoader.loadClass(s"$packageName.BuildInfo").getDeclaredMethod("toString").invoke(null)
    if (BuildInfo.toString != info.toString)
      sys.error(s"build info does not match")

    val constructor = env.getDeclaredConstructor(classOf[Environment.Context])
    constructor.setAccessible(true)

    constructor.newInstance(context)
  }


  /**
   * Marker trait indicating raw (encoded) Isabelle symbol text.
   *
   * @see [[Environment#decode]]
   * @see [[Environment#encode]]
   */
  sealed trait Raw

  /**
   * Marker trait indicating Unicode (decoded) Isabelle symbol text.
   *
   * @see [[Environment#decode]]
   * @see [[Environment#encode]]
   */
  sealed trait Unicode

  /** Bundles all requirements to instantiate an [[Environment environment]]. */
  case class Context(home: Path, user: Path, components: List[Path])(implicit val scheduler: Scheduler) {
    def executorService = scheduler.toExecutorService
  }

}


/**
 * Abstract interface for an Isabelle environment of a particular
 * [[Version version]] in a path with an underlying PIDE machinery.
 *
 * As opposed to a mere logic-less `[[info.hupel.isabelle.setup.Setup Setup]]`,
 * an environment knows how to manage Isabelle processes. It can also manage
 * multiple running processes at the same time.
 *
 * A subclass of this class is called ''implementation'' throughout
 * `libisabelle`.
 *
 * It is highly recommended to use
 * `[[info.hupel.isabelle.setup.Setup#makeEnvironment(resolver* Setup.makeEnvironment]]`
 * to instantiate implementations.
 *
 * While implementations may be created freely by users, it is recommended to
 * only use the bundled implementations for the supported Isabelle versions.
 * By convention, they live in the package specified in
 * `[[Environment.packageName]]`.
 *
 * ''Contract''
 *
 *   - An implementation is a subclass of this class.
 *   - The class name of the implementation must be `Environment`. There must
 *     be a `BuildInfo` class in the same package.
 *   - Implementations must be final and provide a constructor with exactly one
 *     argument (of type `[[Environment.Context]]`). There must be no other
 *     constructors. The constructor should be `private`.
 *   - Implementations must be annotated with
 *     `[[info.hupel.isabelle.api.Implementation Implementation]]`, where the
 *     given [[info.hupel.isabelle.api.Implementation.identifier identifier]]
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
abstract class Environment protected(val context: Environment.Context) { self =>

  protected final val logger = getLogger

  logger.debug(s"Instantiating environment at ${context.home} (with user storage ${context.user}) ...")

  final val home = context.home.toAbsolutePath
  final val user = context.user.toAbsolutePath
  final implicit val scheduler: Scheduler = context.scheduler

  final val version: Version = Environment.getVersion(getClass())

  final val variables: Map[String, String] = Map(
    "ISABELLE_VERSION" -> version.identifier,
    "LIBISABELLE_GIT" -> BuildInfo.gitHeadCommit.getOrElse(""),
    "LIBISABELLE_VERSION" -> BuildInfo.version
  )

  final val etc = user.resolve(".isabelle").resolve(s"Isabelle${version.identifier}").resolve("etc")
  final val etcComponents = etc.resolve("components")

  final def setEtcComponents(): Unit =
    if (!context.components.isEmpty) {
      logger.debug(s"Initializing components ...")

      Files.createDirectories(etc)

      if (Files.exists(etcComponents))
        logger.error(s"Components catalog $etcComponents already exists")

      val out = Files.newBufferedWriter(etcComponents, Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW)
      context.components.foreach { c =>
        val path = isabellePath(c.toAbsolutePath().toString)
        logger.debug(s"Adding component $path ...")
        out.write(path)
        out.write("\n")
      }
      out.close()
    }

  final def cleanEtcComponents(): Unit = {
    Files.deleteIfExists(etcComponents)
    ()
  }


  override def toString: String =
    s"$version at $home"

  protected[isabelle] def isabelleSetting(name: String): String
  protected[isabelle] def isabellePath(path: String): String

  protected[isabelle] def build(config: Configuration): Int

  protected[isabelle] val functionTag: String
  protected[isabelle] val protocolTag: String
  protected[isabelle] val initTag: String
  protected[isabelle] val exitTag: String
  protected[isabelle] val printTags: Set[String]

  protected[isabelle] type Session

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XML.Body) => Unit): Session
  protected[isabelle] def sendOptions(session: Session): Unit
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]): Unit
  protected[isabelle] def dispose(session: Session): Unit

  def decode(text: String @@ Environment.Raw): String @@ Environment.Unicode
  def encode(text: String @@ Environment.Unicode): String @@ Environment.Raw

  def settings: Map[String, String]

  def exec(tool: String, args: List[String]): Int

}
