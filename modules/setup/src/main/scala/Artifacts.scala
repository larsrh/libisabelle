package info.hupel.isabelle.setup

import java.io.File
import java.nio.file._

import scala.concurrent.{ExecutionContext, Future}

import coursier._

import org.log4s._

import info.hupel.isabelle.Platform

object Artifacts {

  private val logger = getLogger

  def fetch(platform: Platform, dependencies: Set[Dependency], offline: Boolean)(implicit ec: ExecutionContext): Future[List[Path]] = {
    logger.debug(s"Fetching artifacts in ${if (offline) "offline" else "online"} mode")

    val coursierStorage = platform.versionedStorage.resolve("coursier")

    val repositories = Seq(
      coursier.Cache.ivy2Local,
      MavenRepository("https://repo1.maven.org/maven2"),
      MavenRepository("https://oss.sonatype.org/content/repositories/releases")
    )

    val downloadLogger = new coursier.Cache.Logger {
      override def foundLocally(url: String, file: File) =
        logger.debug(s"Using local cache of $url")
      override def downloadingArtifact(url: String, file: File) = {
        if (offline)
          sys.error(s"Attempting to download from $url, but running in offline mode. Aborting.")
        else
          logger.debug(s"Downloading artifact from $url ...")
      }
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

    val fetch =
      if (offline)
        Fetch.from(
          repositories,
          cache(CachePolicy.LocalOnly)
        )
      else
        Fetch.from(
          repositories,
          cache(CachePolicy.LocalUpdateChanging),
          cache(CachePolicy.LocalOnly),
          cache(CachePolicy.FetchMissing)
        )

    def fetchArtifact(artifact: Artifact) = {
      def file(cachePolicy: CachePolicy) =
        Cache.file(artifact, cache = coursierStorage.toFile, logger = Some(downloadLogger), cachePolicy = cachePolicy)

      val task =
        if (offline)
          file(CachePolicy.LocalOnly)
        else
          file(CachePolicy.LocalOnly) orElse file(CachePolicy.FetchMissing)

      task.run.toScalaFuture
    }

    val result = platform.withAsyncLock { () =>
      for {
        resolution <- Resolution(dependencies).process.run(fetch).toScalaFuture
        artifacts = {
            if (!resolution.isDone)
              sys.error("not converged")
            else if (!resolution.metadataErrors.isEmpty)
              sys.error(s"errors: ${resolution.metadataErrors}")
            else if (!resolution.conflicts.isEmpty)
              sys.error(s"conflicts: ${resolution.conflicts}")
            else
              resolution.artifacts.toSet
          }
        res <- Future.traverse(artifacts.toList)(fetchArtifact)
      }
      yield
        res.map(_.fold(err => sys.error(err.toString), _.toPath))
    }
    result.foreach(_ => logger.debug("Finished fetching artifacts"))
    result
  }

}
