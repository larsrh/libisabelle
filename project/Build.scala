import sbt._
import Keys._

object build extends Build {

  lazy val standardSettings = Seq(
    organization := "cs.tum.edu.isabelle",
    scalaVersion := "2.11.5"
  )

  lazy val root = Project(
    id = "root",
    base = file("."),
    settings = standardSettings,
    aggregate = Seq(pideCore, examples)
  )

  lazy val pideCore = Project(
    id = "pide-core",
    base = file("pide-core"),
    settings = standardSettings ++ Seq(
      name := "pide-core",
      libraryDependencies ++= Seq(
        "org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.2",
        "org.tukaani" % "xz" % "1.2"
      )
    )
  )

  lazy val examples = Project(
    id = "examples",
    base = file("examples"),
    settings = standardSettings ++ Seq(
      name := "examples"
    ),
    dependencies = Seq(pideCore)
  )

}
