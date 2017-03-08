package info.hupel.isabelle.setup

import java.io.File
import java.nio.file._

import scala.concurrent.{ExecutionContext, Future}

import coursier._

import org.log4s._

import info.hupel.isabelle.api.Platform

object Artifacts {

  private val logger = getLogger

  def fetch(platform: Platform, dependencies: Set[Dependency])(implicit ec: ExecutionContext): Future[List[Path]] = {
    val coursierStorage = platform.versionedStorage.resolve("coursier")

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

    def cache(policy: CachePolicy) =
      Cache.fetch(cache = coursierStorage.toFile, logger = Some(downloadLogger), cachePolicy = policy)

    val fetch = Fetch.from(
      repositories,
      cache(CachePolicy.LocalUpdateChanging),
      cache(CachePolicy.LocalOnly),
      cache(CachePolicy.FetchMissing)
    )

    def fetchArtifact(artifact: Artifact, cachePolicy: CachePolicy) =
      Cache.file(artifact, cache = coursierStorage.toFile, logger = Some(downloadLogger), cachePolicy = cachePolicy)

    platform.withAsyncLock { () =>
      for {
        resolution <- Resolution(dependencies).process.run(fetch).toScalaFuture
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
