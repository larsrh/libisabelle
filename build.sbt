import UnidocKeys._

lazy val standardSettings = Seq(
  organization := "info.hupel",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.6", "2.11.8"),
  javacOptions += "-Xlint:unchecked",
  homepage := Some(url("http://lars.hupel.info/libisabelle/")),
  licenses := Seq(
    "MIT" -> url("http://opensource.org/licenses/MIT"),
    "BSD" -> url("http://opensource.org/licenses/BSD-3-Clause")
  ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  publishTo := {
    val nexus = "https://oss.sonatype.org/"
    if (version.value.endsWith("SNAPSHOT"))
      Some("snapshots" at nexus + "content/repositories/snapshots")
    else
      Some("releases"  at nexus + "service/local/staging/deploy/maven2")
  },
  pomExtra := (
    <developers>
      <developer>
        <id>larsrh</id>
        <name>Lars Hupel</name>
        <url>http://lars.hupel.info</url>
      </developer>
    </developers>
    <scm>
      <connection>scm:git:github.com/larsrh/libisabelle.git</connection>
      <developerConnection>scm:git:git@github.com:larsrh/libisabelle.git</developerConnection>
      <url>https://github.com/larsrh/libisabelle</url>
    </scm>
  ),
  credentials += Credentials(
    Option(System.getProperty("build.publish.credentials")) map (new File(_)) getOrElse (Path.userHome / ".ivy2" / ".credentials")
  ),
  autoAPIMappings := true
)

lazy val warningSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-encoding", "UTF-8",
    "-unchecked",
    "-Xlint",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  ),
  scalacOptions in (Compile, doc) := Seq(
    "-encoding", "UTF-8"
  )
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val acyclicSettings = Seq(
  libraryDependencies += "com.lihaoyi" %% "acyclic" % "0.1.4" % "provided",
  autoCompilerPlugins := true,
  addCompilerPlugin("com.lihaoyi" %% "acyclic" % "0.1.4")
)

lazy val apiBuildInfoKeys = Seq[BuildInfoKey](
  version,
  scalaVersion,
  scalaBinaryVersion,
  organization,
  git.gitHeadCommit
)

lazy val logback = "ch.qos.logback" % "logback-classic" % "1.1.7"


lazy val root = project.in(file("."))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(
    pideInterface, libisabelle, setup,
    tests, docs, examples,
    cli,
    pide2015, pide2016,
    pidePackage
  )

lazy val docs = project.in(file("docs"))
  .settings(moduleName := "libisabelle-docs")
  .settings(standardSettings)
  .settings(unidocSettings)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(pideInterface, libisabelle, setup),
    doc in Compile := (doc in ScalaUnidoc).value,
    target in unidoc in ScalaUnidoc := crossTarget.value / "api"
  )

lazy val pideInterface = project.in(file("pide-interface"))
  .settings(moduleName := "pide-interface")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(acyclicSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    buildInfoKeys := apiBuildInfoKeys,
    buildInfoPackage := "edu.tum.cs.isabelle.api",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.0",
      "org.log4s" %% "log4s" % "1.2.1"
    )
  )

lazy val libisabelle = project
  .dependsOn(pideInterface)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(acyclicSettings)
  .settings(Seq(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "0.4.1",
      "io.monix" %% "monix" % "2.0-RC9"
    )
  ))

lazy val setup = project.in(file("setup"))
  .dependsOn(libisabelle, pideInterface)
  .settings(moduleName := "libisabelle-setup")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(acyclicSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "1.0.0-M11",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M11",
      "com.github.fge" % "java7-fs-more" % "0.2.0",
      "com.google.code.findbugs" % "jsr305" % "3.0.1" % "compile",
      "org.apache.commons" % "commons-compress" % "1.10",
      "org.apache.commons" % "commons-lang3" % "3.4",
      "commons-io" % "commons-io" % "2.4"
    )
  )


// PIDE implementations

def pide(version: String) = Project(s"pide$version", file(s"pide/$version"))
  .dependsOn(pideInterface % "provided")
  .settings(moduleName := s"pide-$version")
  .settings(standardSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(Seq(
    buildInfoKeys := apiBuildInfoKeys,
    buildInfoPackage := "edu.tum.cs.isabelle.impl",
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.10")
        Seq()
      else
        Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4")
    },
    assemblyJarName := s"${moduleName.value}-assembly.jar"
  ))

lazy val pide2015 = pide("2015")
lazy val pide2016 = pide("2016")


def assemblyGenerator(p: Project): Def.Initialize[Task[Seq[File]]] =
  (streams, assembly in p, resourceManaged) map { (streams, source, targetDir) =>
    val target = targetDir / source.getName
    val log = streams.log
    log.info(s"Copying assembly $source to $target ...")
    IO.copyFile(source, target, preserveLastModified = true)
    Seq(target)
  }

lazy val pidePackage = project.in(file("pide-package"))
  .dependsOn(pideInterface)
  .settings(moduleName := "pide-package")
  .settings(standardSettings)
  .settings(
    resourceGenerators in Compile <+= assemblyGenerator(pide2015),
    resourceGenerators in Compile <+= assemblyGenerator(pide2016)
  )


// Tests

lazy val tests = project.in(file("tests"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(acyclicSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "3.6.5" % "test",
      "org.specs2" %% "specs2-scalacheck" % "3.6.5" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.5" % "test",
      logback % "test"
    ),
    parallelExecution in Test := false
  )


// Standalone applications

lazy val cli = project.in(file("cli"))
  .dependsOn(setup, pidePackage)
  .settings(moduleName := "libisabelle-cli")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(acyclicSettings)
  .settings(libraryDependencies += logback)


// Examples

lazy val examples = project.in(file("examples"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(libraryDependencies += logback)


// Workbench

lazy val workbench = project.in(file("workbench"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(
    libraryDependencies += logback,
    initialCommands in console := """
      import edu.tum.cs.isabelle._
      import edu.tum.cs.isabelle.api._
      import edu.tum.cs.isabelle.pure._
      import edu.tum.cs.isabelle.hol._
      import edu.tum.cs.isabelle.setup._
      import scala.concurrent.duration.Duration
      import scala.concurrent.Await
      import scala.concurrent.ExecutionContext.Implicits.global
      import java.nio.file.Paths

      val setup = Await.result(Setup.defaultSetup(Version("2016")).toOption.get, Duration.Inf)
      val env = Await.result(setup.makeEnvironment, Duration.Inf)
      val resources = Resources.dumpIsabelleResources()
      val config = resources.makeConfiguration(Nil, "HOL-Protocol")
      System.build(env, config)
      val system = Await.result(System.create(env, config), Duration.Inf)

      val main = Theory(system, "Main")"""
  )


// Release stuff

import ReleaseTransformations._

releaseProcess := Seq[ReleaseStep](
  checkSnapshotDependencies,
  inquireVersions,
  runClean,
  setReleaseVersion,
  commitReleaseVersion,
  tagRelease,
  ReleaseStep(action = Command.process("publishSigned", _), enableCrossBuild = true),
  setNextVersion,
  commitNextVersion,
  ReleaseStep(action = Command.process("sonatypeReleaseAll", _), enableCrossBuild = true)
)


// Miscellaneous

cancelable in Global := true
