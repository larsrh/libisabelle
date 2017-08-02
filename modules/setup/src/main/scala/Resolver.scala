package info.hupel.isabelle.setup

import java.io.FileNotFoundException
import java.nio.file._

import scala.concurrent.{Future, ExecutionContext}

import coursier._

import org.log4s._

import info.hupel.isabelle.Platform
import info.hupel.isabelle.api.{BuildInfo, Version}

/**
 * Function preparing a classpath containing an appropriate
 * [[info.hupel.isabelle.api.Environment environment]].
 *
 * Instances are available in the [[Resolver$ companion object]].
 */
trait Resolver { self =>

  def resolve(platform: Platform, version: Version.Stable)(implicit ec: ExecutionContext): Future[List[Path]]

  def orElse(that: Resolver) = new Resolver {
    def resolve(platform: Platform, version: Version.Stable)(implicit ec: ExecutionContext) =
      // recoverWith instead of fallbackTo because the latter is eager
      // we don't want to run the second future unless necessary
      self.resolve(platform, version).recoverWith { case _ =>
        Resolver.logger.warn("Resolution failed, trying fallback")
        that.resolve(platform, version)
      }
  }

}

/** [[Resolver resolver]] instances. */
object Resolver {

  private val logger = getLogger

  /**
   * Default resolver: look in the [[Classpath classpath]] first, then resolve
   * via [[Maven]].
   */
  def Default =
    Classpath orElse Maven


  /**
   * Classpath-based [[Resolver resolver]].
   *
   * When looking up a [[info.hupel.isabelle.api.Version version]], it expects
   * a resource named `pide-\$version-assembly.jar` in the classpath. Because
   * Java URLs cannot be nested, this resolver then writes this resource to a
   * temporary file.
   */
  object Classpath extends Resolver {

    private val logger = getLogger

    def resolve(platform: Platform, version: Version.Stable)(implicit ec: ExecutionContext) = {
      logger.debug("Trying to resolve PIDE jar from classpath")
      val classLoader = getClass.getClassLoader
      val fileName = s"pide-${version.identifier}-assembly.jar"
      Option(classLoader.getResourceAsStream(fileName)) match {
        case Some(stream) =>
          val target = Files.createTempFile("pide", ".jar")
          target.toFile.deleteOnExit()
          logger.debug(s"Dumping PIDE jar to $target ...")
          Files.copy(stream, target, StandardCopyOption.REPLACE_EXISTING)
          stream.close()
          Future.successful { List(target) }
        case None =>
          Future.failed { new FileNotFoundException(s"PIDE ${version.identifier} not on classpath") }
      }
    }

  }

  /**
   * Maven-/Ivy-based [[Resolver resolver]].
   *
   * Uses `coursier` under the hood to resolve artifacts from Maven and Ivy
   * repositories.
   */
  object Maven extends Resolver {

    def resolve(platform: Platform, version: Version.Stable)(implicit ec: ExecutionContext) = {
      logger.debug("Trying to resolve PIDE jar from Maven Central")
      val dependency = Dependency(
        Module(BuildInfo.organization, s"pide-${version.identifier}_${BuildInfo.scalaBinaryVersion}"),
        BuildInfo.version
      )
      Artifacts.fetch(platform, Set(dependency))
    }

  }

}
