package info.hupel.isabelle.setup

import java.io.OutputStreamWriter
import java.nio.file.{Files, Path}

import org.eclipse.jgit.api._
import org.eclipse.jgit.lib.TextProgressMonitor
import org.eclipse.jgit.storage.file._

import org.log4s._

trait Devel {
  def init(path: Path): Unit
  def update(path: Path): Unit
}

case class GitDevel(url: String, branch: String) extends Devel {

  private val logger = getLogger

  private val monitor = new TextProgressMonitor(new OutputStreamWriter(Console.err))

  def init(path: Path): Unit = {
    logger.debug(s"Cloning $branch from $url into $path")
    Files.createDirectories(path)
    new CloneCommand()
      .setDirectory(path.toFile)
      .setURI(url)
      .setBranch(branch)
      .setProgressMonitor(monitor)
      .call()
    ()
  }
  def update(path: Path): Unit = {
    logger.debug(s"Fetching $branch from $url into $path")
    val repo = new FileRepositoryBuilder()
      .findGitDir(path.toFile)
      .setup()
      .build()
    new Git(repo).pull()
      .setRemoteBranchName(branch)
      .call()
    ()
  }
}

object Devel {

  val knownDevels: Map[String, Devel] = Map(
    "isabelle-mirror" -> GitDevel("https://github.com/isabelle-prover/mirror-isabelle.git", "master")
  )

}
