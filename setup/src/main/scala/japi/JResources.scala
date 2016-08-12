package info.hupel.isabelle.japi

import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.{List => JList}

import scala.collection.JavaConverters._

import cats.data.Xor

import info.hupel.isabelle.api._
import info.hupel.isabelle.setup._

object JResources {

  def dumpIsabelleResources(): JResources =
    Resources.dumpIsabelleResources() match {
      case Xor.Right(resources) => new JResources(resources)
      case Xor.Left(error) => sys.error(error.explain)
    }

}

class JResources private(resources: Resources) {

  def getResources(): Resources = resources

  def makeConfiguration(auxPaths: JList[Path], name: String): Configuration =
    resources.makeConfiguration(auxPaths.asScala.toList, name)

  def findTheory(theory: Path): String =
    resources.findTheory(theory).getOrElse(throw new FileNotFoundException(theory.toString))

}
