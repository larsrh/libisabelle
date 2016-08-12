package info.hupel.isabelle.setup

import java.net.URL
import java.nio.charset.Charset
import java.nio.file._

import scala.collection.JavaConverters._
import scala.io.Source

import cats.data.Xor

import org.log4s._

import info.hupel.isabelle.api.{Configuration, Version}

import acyclic.file

object Resources {

  sealed abstract class Error(val explain: String)
  case object Absent extends Error("No resources found in classpath")
  case class DuplicatedFiles(files: List[String]) extends Error(s"Duplicated resources in classpath: ${files.mkString(", ")}")

  private val logger = getLogger

  def dumpIsabelleResources(): Xor[Error, Resources] =
    dumpIsabelleResources(Files.createTempDirectory("libisabelle_resources"), getClass.getClassLoader)

  def dumpIsabelleResources(path: Path, classLoader: ClassLoader): Xor[Error, Resources] = {
    val files = classLoader.getResources(".libisabelle/.files").asScala.toList.flatMap { url =>
      logger.debug(s"Found Isabelle resource set at $url")
      Source.fromURL(url, "UTF-8").getLines.toList
    }

    if (files.nonEmpty) {
      logger.debug(s"Dumping Isabelle resources to $path ...")

      if (files.distinct != files) {
        val fileSet = files.toSet
        val duplicates = files diff fileSet.toList
        Xor.left(DuplicatedFiles(duplicates))
      }
      else {
        for (file <- files) {
          val target = path resolve file
          Files.createDirectories(target.getParent)
          val in = classLoader.getResourceAsStream(s".libisabelle/$file")
          Files.copy(in, target)
          in.close()
        }

        val stream = Files.newDirectoryStream(path)
        val roots = stream.asScala.toList.map(_.getFileName.toString).mkString("\n")
        stream.close()

        val out = Files.newBufferedWriter(path resolve "ROOTS", Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW)
        out.write(roots)
        out.close()

        Xor.right(Resources(path))
      }
    }
    else {
      Xor.left(Absent)
    }
  }

}

final case class Resources(path: Path) {

  def makeConfiguration(auxPaths: List[Path], name: String): Configuration = {
    Configuration(path :: auxPaths, name)
  }

  def findTheory(theory: Path): Option[String] = {
    val fullPath = path.resolve(theory)
    if (Files.exists(fullPath))
      Some(fullPath.toRealPath().toString.stripSuffix(".thy"))
    else
      None
  }

}
