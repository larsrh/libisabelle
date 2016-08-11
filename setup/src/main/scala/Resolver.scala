package info.hupel.isabelle.setup

import java.io.{File, FileNotFoundException}
import java.nio.file._
import java.net.URL

import scala.concurrent.{Future, ExecutionContext}

import org.apache.commons.io.IOUtils

import coursier._

import org.log4s._

import info.hupel.isabelle.api.{BuildInfo, Version}

import acyclic.file

trait Resolver { self =>

  def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext): Future[List[Path]]

  def orElse(that: Resolver) = new Resolver {
    def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext) =
      // recoverWith instead of fallbackTo because the latter is eager
      // we don't want to run the second future unless necessary
      self.resolve(platform, version).recoverWith { case _ => that.resolve(platform, version) }
  }

}

object Resolver {

  def Default =
    Classpath orElse Maven


  object Classpath extends Resolver {

    private val logger = getLogger

    def resolve(platform: Platform, version: Version)(implicit ec: ExecutionContext) = {
      val classLoader = getClass.getClassLoader
      val fileName = s"pide-${version.identifier}-assembly.jar"
      Option(classLoader.getResourceAsStream(fileName)) match {
        case Some(stream) =>
          val target = Files.createTempFile("pide", ".jar")
          logger.debug(s"Dumping PIDE jar to $target ...")
          IOUtils.copy(stream, Files.newOutputStream(target, StandardOpenOption.WRITE))
          Future.successful { List(target) }
        case None =>
          Future.failed { new FileNotFoundException(s"PIDE ${version.identifier} not on classpath") }
      }
    }

  }

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
