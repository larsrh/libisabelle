package info.hupel.isabelle.api

import java.net.URLClassLoader
import java.nio.charset.Charset
import java.nio.file._

import scala.util.control.ControlThrowable

import monix.execution.Scheduler

import org.log4s._

import shapeless.tag._

case class ExitTrap(rc: Int) extends ControlThrowable

object Environment {

  private val logger = getLogger

  private def getVersion(clazz: Class[_ <: Environment]): Version.Stable =
    Option(clazz.getAnnotation(classOf[Implementation])) match {
      case Some(annot) =>
        Version.Stable(annot.identifier)
      case _ =>
        sys.error("malformed implementation")
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

    import context.scheduler

    constructor.newInstance(context.copy(options =
      OptionKey.Bool("ML_statistics").set(false) :: context.options
    ))
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
  case class Context(home: Path, user: Path, components: List[Path], options: List[OptionKey.Update])(implicit val scheduler: Scheduler) {
    def executorService = scheduler.toExecutorService

    // FIXME move to different location?
    def etc(version: Version) = version match {
      case Version.Devel(_) => user.resolve(".isabelle").resolve("etc")
      case Version.Stable(identifier) => user.resolve(".isabelle").resolve(s"Isabelle$identifier").resolve("etc")
    }
    def etcComponents(version: Version) = etc(version).resolve("components")
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
abstract class Environment protected(val context: Environment.Context, versionOverride: Option[Version] = None, checkClassLoader: Boolean = true) { self =>

  if (checkClassLoader && getClass.getClassLoader == classOf[Environment].getClassLoader)
    sys.error("Environment not loaded in an isolated class loader, this is unsupported")

  protected final val logger = getLogger
  protected final val evalCommand = "libisabelle_eval"

  logger.debug(s"Instantiating environment at ${context.home} (with user storage ${context.user}) ...")

  final val home = context.home.toAbsolutePath
  final val user = context.user.toAbsolutePath
  final implicit val scheduler: Scheduler = context.scheduler

  final val version: Version = versionOverride.getOrElse(Environment.getVersion(getClass()))

  final val variables: Map[String, String] = Map(
    "LIBISABELLE_GIT" -> BuildInfo.gitHeadCommit.getOrElse(""),
    "LIBISABELLE_VERSION" -> BuildInfo.version
  ) ++ (version match {
    case Version.Devel(_) => Map()
    case Version.Stable(identifier) => Map("ISABELLE_VERSION" -> identifier)
  })

  final val etc = context.etc(version)
  final val etcComponents = context.etcComponents(version)

  protected final def setEtcComponents(): Unit =
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

  protected final def cleanEtcComponents(): Unit = {
    Files.deleteIfExists(etcComponents)
    ()
  }

  protected final def protocolTheory(loaded: Set[String]): Option[String] =
    if (loaded.contains("Protocol.Protocol"))
      None
    else {
      // this only works if resources are registered
      val resourcesHome = isabelleSetting("LIBISABELLE_RESOURCES_HOME")
      if (resourcesHome.isEmpty)
        sys.error("protocol not loaded but component not registered")

      logger.info("Protocol theory not contained in image, scheduling to be loaded ...")

      if (loaded.contains("Main"))
        Some(s"HOL-Protocol.Protocol_Main")
      else
        Some(s"Protocol.Protocol_Pure")
    }

  override def toString: String =
    s"$version at $home"

  protected[isabelle] def build(config: Configuration): Int

  protected[isabelle] val functionTag: String
  protected[isabelle] val protocolTag: String
  protected[isabelle] val initTag: String
  protected[isabelle] val exitTag: String
  protected[isabelle] val printTags: Set[String]

  protected[isabelle] type Session

  protected[isabelle] def create(config: Configuration, consumer: (Markup, XML.Body) => Unit): (Session, Option[String])
  protected[isabelle] def sendOptions(session: Session): Unit
  protected[isabelle] def sendCommand(session: Session, name: String, args: List[String]): Unit
  protected[isabelle] def dispose(session: Session): Unit

  protected[isabelle] final def eval(session: Session, ml: String): Unit =
    sendCommand(session, evalCommand, List(ml))

  def isabelleSetting(name: String): String
  def isabellePath(path: String): String

  def decode(text: String @@ Environment.Raw): String @@ Environment.Unicode
  def encode(text: String @@ Environment.Unicode): String @@ Environment.Raw

  def exec(tool: String, args: List[String]): Int

}
