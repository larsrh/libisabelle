package info.hupel.isabelle.setup

import java.nio.charset.Charset
import java.nio.file._

import scala.collection.JavaConverters._
import scala.io.Source

import cats.instances.either._
import cats.instances.list._
import cats.syntax.traverse._

import org.log4s._

import info.hupel.isabelle.api.Configuration

/** Utilities to extract [[Resources resources]] from the classpath. */
object Resources {

  sealed abstract class Error(val explain: String)
  case object Absent extends Error("No resources found in classpath")
  case class DuplicatedFiles(files: List[String]) extends Error(s"Duplicated resources in classpath: ${files.mkString(", ")}")
  case class MissingFiles(files: List[String]) extends Error(s"Missing files in classpath: ${files.mkString(", ")}")

  private val logger = getLogger

  /**
   * Shorthand for `[[dumpIsabelleResources(path* dumpIsabelleResources]]
   * using a temporary path and the current class loader.
   */
  def dumpIsabelleResources(): Either[Error, Resources] =
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
   * The specified path will then be turned into an Isabelle "component" (refer
   * to the Isabelle system manual for information about components) by
   * creating an `etc/settings` file, which we will call ''resources
   * component''. The root directory of the resources component is available
   * in the environment variable `LIBISABELLE_RESOURCES_HOME`.
   *
   * Resources are checked for contained components and session root
   * directories. This is done by looking for `ROOT`, `ROOTS`, and
   * `etc/settings` in direct subdirectories that have been created below the
   * specified path. Components are added as subcomponents to the resources
   * component (`$LIBISABELLE_RESOURCES_HOME/etc/components`) and session root
   * directories to `$LIBISABELLE_RESOURCES_HOME/ROOTS`. Session roots that are
   * also components are only added as components, because components are
   * automatically discovered as session roots.
   *
   * Note that this will only work correctly if the specified path ultimately
   * gets registered as an Isabelle component. Otherwise, subcomponents that
   * are also session roots do not get discovered properly.
   */
  def dumpIsabelleResources(path: Path, classLoader: ClassLoader): Either[Error, Resources] = {
    val files = classLoader.getResources(".libisabelle/.files").asScala.toList.flatMap { url =>
      logger.debug(s"Found Isabelle resource set at $url")
      Source.fromURL(url, "UTF-8").getLines.toList
    }.filterNot(_.isEmpty)

    def filterFor(markers: String*) =
      (for {
        file <- files
        subdir = Paths.get(file).subpath(0, 1)
        if markers.exists(m => files.contains(s"$subdir/$m"))
      } yield subdir).distinct

    def writeList(target: Path, list: List[Path]) = {
      val out = Files.newBufferedWriter(target, Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW)
      out.write(list.distinct.mkString("\n"))
      out.close()
    }

    if (files.nonEmpty) {
      logger.debug(s"Dumping Isabelle resources to $path ...")

      if (files.distinct != files) {
        val fileSet = files.toSet
        val duplicates = files diff fileSet.toList
        Left(DuplicatedFiles(duplicates))
      }
      else {
        val result = files.traverse { file =>
          val target = path resolve file
          Files.createDirectories(target.getParent)
          val in = classLoader.getResourceAsStream(s".libisabelle/$file")
          if (in == null) {
            Left(List(file))
          }
          else {
            Files.copy(in, target)
            in.close()
            Right(())
          }
        }

        result match {
          case Left(files) =>
            Left(MissingFiles(files))
          case Right(_) =>
            val components = filterFor("etc/settings")
            components.foreach(subdir => logger.debug(s"Found component at $path/$subdir"))
            val target = path resolve "etc"
            Files.createDirectories(target)
            writeList(target resolve "components", components)

            val roots = filterFor("ROOT", "ROOTS")
            roots.foreach(subdir => logger.debug(s"Found session root at $path/$subdir"))
            writeList(path resolve "ROOTS", roots diff components)

            val out = Files.newBufferedWriter(target resolve "settings", Charset.forName("UTF-8"), StandardOpenOption.CREATE_NEW)
            out.write("""LIBISABELLE_RESOURCES_HOME="$COMPONENT"""")
            out.close()

            Right(Resources(path, roots.map(path.resolve)))
        }
      }
    }
    else {
      Left(Absent)
    }
  }

}

/**
 * A file system location containing Isabelle sources.
 *
 * In almost all cases, instances of this object should be produced using
 * `[[Resources.dumpIsabelleResources(path* Resources.dumpIsabelleResources]]`
 * (see its documentation for details).
 */
final case class Resources private(component: Path, roots: List[Path]) {

  /**
   * Produces a [[info.hupel.isabelle.api.Configuration configuration]] with
   * the specified paths, preceded by the location from this object.
   */
  def asSessionsConfiguration(auxPaths: List[Path], name: String): Configuration =
    Configuration(roots ::: auxPaths, name)

  /**
   * Checks presence of a theory file in this location. The input path should
   * be a relative file name (e.g. `src/HOL/ex/Seq.thy`). If present, the
   * output is an Isabelle-conforming theory import path
   * (e.g. `src/HOL/ex/Seq`).
   */
  def findTheory(theory: Path): Option[String] = {
    val fullPath = component.resolve(theory)
    if (Files.exists(fullPath))
      Some(fullPath.toRealPath().toString.stripSuffix(".thy"))
    else
      None
  }

}
