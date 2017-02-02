package info.hupel.isabelle.setup

import java.net.URL
import java.nio.file._
import java.nio.file.attribute.PosixFilePermissions

import scala.util.Try

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream
import org.apache.commons.lang3.SystemUtils

/** Downloading and unpacking `tar` archives. All operations are blocking. */
object Tar {

  val execPermissions = PosixFilePermissions.fromString("rwxr-xr-x")

  def download(url: URL): Try[TarArchiveInputStream] =
    Try(new TarArchiveInputStream(new GzipCompressorInputStream(url.openStream())))

  def extractTo(path: Path, tar: TarArchiveInputStream): Try[Path] = Try {
    def next() = Option(tar.getNextTarEntry())

    @annotation.tailrec
    def go(entry: Option[TarArchiveEntry], paths: List[Path]): List[Path] = entry match {
      case None =>
        paths.reverse
      case Some(entry) =>
        val name = entry.getName
        val subpath = path.resolve(name).normalize

        if (subpath.startsWith(path) && !Files.exists(subpath, LinkOption.NOFOLLOW_LINKS)) {
          Files.createDirectories(subpath.getParent)
          if (entry.isDirectory)
            Files.createDirectory(subpath)
          else if (entry.isSymbolicLink)
            Files.createSymbolicLink(subpath, Paths.get(entry.getLinkName))
          else if (entry.isLink)
            Files.createLink(subpath, path.resolve(Paths.get(entry.getLinkName)))
          else if (entry.isFile) {
            Files.copy(tar, subpath)
            if (!SystemUtils.IS_OS_WINDOWS && (entry.getMode % 2 == 1))
              Files.setPosixFilePermissions(subpath, execPermissions)
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
