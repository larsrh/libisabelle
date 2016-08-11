package info.hupel.isabelle.setup

import java.net.URL
import java.nio.file._

import scala.collection.JavaConverters._
import scala.io.Source

import org.apache.commons.io.IOUtils

import org.log4s._

import info.hupel.isabelle.api.{Configuration, Version}

import acyclic.file

object Resources {

  private val logger = getLogger

  def dumpIsabelleResources(): Resources =
    dumpIsabelleResources(Files.createTempDirectory("libisabelle_resources"), getClass.getClassLoader)

  def dumpIsabelleResources(path: Path, classLoader: ClassLoader): Resources = {
    def getList(url: URL): (String, List[String]) = {
      logger.debug(s"Found Isabelle resource set at $url")
      val lines = Source.fromURL(url, "UTF-8").getLines.toList
      (lines.head, lines.tail)
    }

    val lists = classLoader.getResources("isabelle/.libisabelle_files").asScala.toList.map(getList)

    val paths =
      for ((name, items) <- lists)
      yield {
        val base = path resolve name
        Files.createDirectories(base)

        logger.debug(s"Dumping Isabelle resource set $name to $base ...")

        for (item <- items) {
          val path = base resolve item
          Files.createDirectories(path.getParent)
          IOUtils.copy(
            classLoader.getResourceAsStream(s"isabelle/$name/$item"),
            Files.newOutputStream(path, StandardOpenOption.CREATE_NEW)
          )
        }

        base
      }

    Resources(paths)
  }

}

final case class Resources(paths: List[Path]) {

  def makeConfiguration(auxPaths: List[Path], name: String): Configuration = {
    Configuration(auxPaths ::: paths.filter(Configuration.isSessionRoot), name)
  }

  def findTheory(theory: Path): Option[String] =
    paths.flatMap { path =>
      val fullPath = path.resolve(theory)
      if (Files.exists(fullPath))
        Some(fullPath.toRealPath().toString.stripSuffix(".thy"))
      else
        None
    }.headOption

}
