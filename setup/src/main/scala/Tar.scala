package edu.tum.cs.isabelle.setup

import java.io.InputStream
import java.net.URL
import java.nio.file._

import scala.concurrent._

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.lang3.SystemUtils

import com.github.fge.filesystem.MoreFiles

import acyclic.file

/** Downloading and unpacking `tar` archives. */
object Tar {

  def download(url: URL): TarArchiveInputStream =
    new TarArchiveInputStream(new GzipCompressorInputStream(url.openStream()))

  def extractTo(path: Path, tar: TarArchiveInputStream)(implicit ec: ExecutionContext): Future[Path] = Future {
    def next() = blocking {
      Option(tar.getNextTarEntry())
    }

    @annotation.tailrec
    def go(entry: Option[TarArchiveEntry], paths: List[Path]): List[Path] = entry match {
      case None =>
        paths.reverse
      case Some(entry) =>
        val name = entry.getName
        val subpath = path.resolve(name).normalize

        if (subpath.startsWith(path) && !Files.exists(subpath, LinkOption.NOFOLLOW_LINKS)) {
          Files.createDirectories(subpath.getParent)
          if (entry.isDirectory) // FIXME how does tar work?
            Files.createDirectory(subpath)
          else if (entry.isSymbolicLink)
            Files.createSymbolicLink(subpath, Paths.get(entry.getLinkName))
          else if (entry.isLink)
            Files.createLink(subpath, path.resolve(Paths.get(entry.getLinkName)))
          else if (entry.isFile)
            blocking {
              Files.copy(tar, subpath)
              if (!SystemUtils.IS_OS_WINDOWS)
                MoreFiles.setMode(subpath, entry.getMode)
            }
          else
            sys.error("unknown tar file entry")
        }
        else
          sys.error("malicious tar file or file already exists")

        val p = if (entry.isDirectory) List(subpath) else Nil

        go(next(), p ::: paths)
    }

    go(next(), Nil).foldLeft(List.empty[Path]) { (roots, path) =>
      if (roots.exists(path.startsWith))
        roots
      else
        path :: roots
    } match {
      case List(root) => root
      case _ => sys.error("untarring created more than one root directory")
    }
  }

}
