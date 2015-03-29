package edu.tum.cs.isabelle

import java.io.File
import java.net.URL
import java.nio.file.{Files, Paths}

import org.apache.commons.lang3.SystemUtils

import org.apache.commons.compress.archivers.tar.{TarArchiveEntry, TarArchiveInputStream}
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream


object Platform {
  case object Linux extends Platform("linux")

  def guess: Option[Platform] =
    if (SystemUtils.IS_OS_LINUX)
      Some(Linux)
    else
      None
}

sealed abstract class Platform(val name: String)

object Isabelle {
  object Isa2014 extends Isabelle("2014") with DefaultIsabelle
  val latest = Isa2014
}

sealed abstract class Isabelle(val version: String) {
  def url(platform: Platform): Option[URL]
}

trait DefaultIsabelle extends Isabelle {

  def base =
    s"https://isabelle.in.tum.de/website-Isabelle$version/dist/Isabelle$version"

  def url(platform: Platform) = platform match {
    case Platform.Linux =>
      Some(new URL(s"${base}_linux.tar.gz"))
  }

}


object Setup {

  def fetchTar(platform: Platform, isabelle: Isabelle): Option[TarArchiveInputStream] =
    isabelle.url(platform) map { url =>
      new TarArchiveInputStream(new GzipCompressorInputStream(url.openStream()))
    }

  def untar(tar: TarArchiveInputStream): File = {
    val path = Files.createTempDirectory("libisabelle").toRealPath()

    def next() = Option(tar.getNextTarEntry())
    
    @annotation.tailrec
    def go(entry: Option[TarArchiveEntry]): Unit = entry match {
      case None => // end of file
      case Some(entry) =>
        val name = entry.getName
        val subpath = path.resolve(name).normalize

        if (subpath.startsWith(path)) {
          Files.createDirectories(subpath.getParent)
          if (entry.isDirectory)
            Files.createDirectories(subpath)
          else if (entry.isSymbolicLink)
            Files.createSymbolicLink(subpath, Paths.get(entry.getLinkName))
          else if (entry.isFile)
            Files.copy(tar, subpath)
          else {
            // TODO warning
            // TODO check fifo, devices
          }
        }
        else {
          // TODO probably malicious tarfile
        }
        go(next())
    }

    go(next())
    path.toFile
  }

}
