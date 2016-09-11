package info.hupel.isabelle.setup

import java.net.URL
import java.nio.charset.Charset
import java.nio.file._

import scala.collection.JavaConverters._
import scala.io.Source

import cats.data.Xor

import org.log4s._

import info.hupel.isabelle.api.{Configuration, Version}

/** Utilities to extract [[Resources resources]] from the classpath. */
object Resources {

  sealed abstract class Error(val explain: String)
  case object Absent extends Error("No resources found in classpath")
  case class DuplicatedFiles(files: List[String]) extends Error(s"Duplicated resources in classpath: ${files.mkString(", ")}")

  private val logger = getLogger

  /**
   * Shorthand for `[[dumpIsabelleResources(path* dumpIsabelleResources]]
   * using a temporary path and the current class loader.
   */
  def dumpIsabelleResources(): Xor[Error, Resources] =
    dumpIsabelleResources(Files.createTempDirectory("libisabelle_resources"), getClass.getClassLoader)

  /**
   * Searches the specified class loader for Isabelle source files.
   *
   * Isabelle sources files can be automatically managed by `libisabelle`. When
   * using an SBT-based build, the
   * `[[https://github.com/larsrh/sbt-libisabelle sbt-libisabelle]]` plugin
   * automatically configures the build to copy the Isabelle sources stored in
   * `src/main/isabelle` into an appropriate location such that they become
   * part of the runtime classpath.
   *
   * This function expects any number of index files in the location
   * `.libisabelle/.files`, containing a list of relative path names where
   * Isabelle source files may be found. For example, an entry `A/tactic.ML`
   * will lead to a lookup for a resource at `.libisabelle/A/tactic.ML`. All
   * index files will be processed. The referenced files will be copied to
   * the specified location. Path names are expected to be encoded in UTF-8,
   * which is the standard encoding Isabelle uses.
   *
   * After that, a `ROOTS` file will be created, listing all direct
   * subdirectories that have been created below the specified path and that
   * are also session root directories. For example, if the files
   * `A/tactic.ML`, `A/ROOTS`, `B/ROOT` and `C/Seq.thy` have been written, the
   * `ROOTS` file will contain `A` and `B`, but not `C`.
   */
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

        val roots =
          (for {
            file <- files
            subdir = Paths.get(file).subpath(0, 1)
            if files.contains(s"$subdir/ROOT") || files.contains(s"$subdir/ROOTS")
          } yield subdir).distinct

        roots.foreach(subdir => logger.debug(s"Found session root at $path/$subdir"))

        val out = Files.newBufferedWriter(path resolve "ROOTS", Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW)
        out.write(roots.distinct.mkString("\n"))
        out.close()

        Xor.right(Resources(path))
      }
    }
    else {
      Xor.left(Absent)
    }
  }

}

/**
 * A file system location containing Isabelle sources.
 *
 * In almost all cases, instances of this object should be produced using
 * `[[Resources.dumpIsabelleResources(path* Resources.dumpIsabelleResources]]`
 * (see its documentation for details).
 *
 * In case you manage Isabelle source files yourself, this class is unneeded
 * and you may want to construct
 * [[info.hupel.isabelle.api.Configuration configurations]] yourself.
 */
final case class Resources(path: Path) {

  /**
   * Produces a [[info.hupel.isabelle.api.Configuration configuration]] with
   * the specified paths, preceded by the location from this object.
   */
  def makeConfiguration(auxPaths: List[Path], name: String): Configuration = {
    Configuration(path :: auxPaths, name)
  }

  /**
   * Checks presence of a theory file in this location. The input path should
   * be a relative file name (e.g. `src/HOL/ex/Seq.thy`). If present, the
   * output is an Isabelle-conforming theory import path
   * (e.g. `src/HOL/ex/Seq`).
   */
  def findTheory(theory: Path): Option[String] = {
    val fullPath = path.resolve(theory)
    if (Files.exists(fullPath))
      Some(fullPath.toRealPath().toString.stripSuffix(".thy"))
    else
      None
  }

}
