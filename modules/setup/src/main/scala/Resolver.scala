package info.hupel.isabelle.setup

import java.io.{File, FileNotFoundException}
import java.nio.file._
import java.net.URL

import scala.concurrent.{Future, ExecutionContext}

import coursier._

import org.log4s._

import info.hupel.isabelle.api.{BuildInfo, Version}

import acyclic.file

/**
 * Function preparing a classpath containing an appropriate
 * [[info.hupel.isabelle.api.Environment environment]].
 *
 * Instances are available in the [[Resolver$ companion object]].
 */
trait Resolver { self =>

  def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext): Future[List[Path]]

  def orElse(that: Resolver) = new Resolver {
    def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext) =
      // recoverWith instead of fallbackTo because the latter is eager
      // we don't want to run the second future unless necessary
      self.resolve(platform, version).recoverWith { case _ => that.resolve(platform, version) }
  }

}

/** [[Resolver resolver]] instances. */
object Resolver {

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

    def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext) = {
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

    private val logger = getLogger

    def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext) = {
      val base = platform.versionedStorage

      val repositories = Seq(
        coursier.Cache.ivy2Local,
        MavenRepository("https://repo1.maven.org/maven2"),
        MavenRepository("https://oss.sonatype.org/content/repositories/releases")
      )

      val downloadLogger = new coursier.Cache.Logger {
        override def downloadingArtifact(url: String, file: File) =
          logger.debug(s"Downloading artifact from $url ...")
        override def downloadedArtifact(url: String, success: Boolean) = {
          val file = url.split('/').last
          if (success)
            logger.debug(s"Successfully downloaded $file")
          else
            logger.error(s"Failed to download $file")
        }
      }

      val fetch = Fetch.from(
        repositories,
        Cache.fetch(logger = Some(downloadLogger), cachePolicy = CachePolicy.LocalOnly),
        Cache.fetch(logger = Some(downloadLogger), cachePolicy = CachePolicy.FetchMissing)
      )

      val dependency =
        Dependency(
          Module(BuildInfo.organization, s"pide-${version.identifier}_${BuildInfo.scalaBinaryVersion}"),
          BuildInfo.version
        )

      def fetchArtifact(artifact: Artifact, cachePolicy: CachePolicy) =
        Cache.file(artifact, logger = Some(downloadLogger), cachePolicy = cachePolicy)

      platform.withAsyncLock { () =>
        for {
          resolution <- Resolution(Set(dependency)).process.run(fetch).toScalaFuture
          artifacts = {
              if (!resolution.isDone)
                sys.error("not converged")
              else if (!resolution.errors.isEmpty)
                sys.error(s"errors: ${resolution.errors}")
              else if (!resolution.conflicts.isEmpty)
                sys.error(s"conflicts: ${resolution.conflicts}")
              else
                resolution.artifacts.toSet
            }
          res <- Future.traverse(artifacts.toList)(artifact =>
            (fetchArtifact(artifact, CachePolicy.LocalOnly)
               orElse fetchArtifact(artifact, CachePolicy.FetchMissing))
              .run.toScalaFuture
          )
        }
        yield
          res.map(_.fold(err => sys.error(err.toString), _.toPath))
      }
    }

  }

}
