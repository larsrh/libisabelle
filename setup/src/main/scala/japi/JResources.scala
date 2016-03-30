package edu.tum.cs.isabelle.japi

import java.io.FileNotFoundException
import java.nio.file.Path
import java.util.{List => JList}

import scala.collection.JavaConverters._

import edu.tum.cs.isabelle.api._
import edu.tum.cs.isabelle.setup._

object JResources {

  def dumpIsabelleResources(): JResources =
    new JResources(Resources.dumpIsabelleResources())

}

class JResources private(resources: Resources) {

  def getResources(): Resources = resources

  def makeConfiguration(auxPaths: JList[Path], name: String): Configuration =
    resources.makeConfiguration(auxPaths.asScala.toList, name)

  def findTheory(theory: Path): String =
    resources.findTheory(theory).getOrElse(throw new FileNotFoundException(theory.toString))

}
