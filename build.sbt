import GhPagesKeys._
import SiteKeys._
import UnidocKeys._
import sbtassembly.AssemblyPlugin.defaultShellScript

lazy val standardSettings = Seq(
  organization := "info.hupel",
  scalaVersion := "2.11.8",
  crossScalaVersions := Seq("2.10.6", "2.11.8", "2.12.1"),
  javacOptions += "-Xlint:unchecked",
  homepage := Some(url("http://lars.hupel.info/libisabelle/")),
  licenses := Seq(
    "MIT" -> url("http://opensource.org/licenses/MIT"),
    "BSD" -> url("http://opensource.org/licenses/BSD-3-Clause")
  ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  resolvers += Resolver.sonatypeRepo("releases"),
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
  autoAPIMappings := true,
  isabelleVersions := Seq("2016", "2016-1")
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
  scalacOptions in (Compile, doc) ~= (_.filterNot(_ == "-Xfatal-warnings"))
)

lazy val noPublishSettings = Seq(
  publish := (),
  publishLocal := (),
  publishArtifact := false
)

lazy val macroSettings = Seq(
  libraryDependencies ++= Seq(
    "org.typelevel" %% "macro-compat" % "1.1.1",
    "org.scala-lang" % "scala-compiler" % scalaVersion.value % "provided",
    "org.scala-lang" % "scala-reflect" % scalaVersion.value % "provided",
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.0" cross CrossVersion.full)
  )
)

lazy val loggingSettings = Seq(
  libraryDependencies ++= Seq(
    "io.rbricks" %% "scalog-backend" % "0.2.1",
    "io.rbricks" %% "scalog-mdc" % "0.2.1",
    // scalac requires this pseudo-transitive dependency of scalog to be present,
    // even though we don't use its functionality here
    // this seems to be an issue for Scala <= 2.11.x
    "com.typesafe" % "config" % "1.3.1" % "provided"
  )
)

lazy val apiBuildInfoKeys = Seq[BuildInfoKey](
  version,
  scalaVersion,
  scalaBinaryVersion,
  organization,
  git.gitHeadCommit
)

lazy val root = project.in(file("."))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(
    pideInterface, libisabelle, setup,
    tests, docs, examples,
    cli,
    pide2016, pide2016_1,
    pidePackage,
    workbench
  )

lazy val docs = project.in(file("modules/docs"))
  .dependsOn(setup, pidePackage)
  .settings(moduleName := "libisabelle-docs")
  .settings(standardSettings)
  .settings(unidocSettings)
  .settings(macroSettings)
  .settings(tutSettings)
  .settings(site.settings ++ site.includeScaladoc("api/nightly"))
  .settings(ghpages.settings)
  .settings(loggingSettings)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(pideInterface, libisabelle, setup),
    doc in Compile := (doc in ScalaUnidoc).value,
    target in unidoc in ScalaUnidoc := crossTarget.value / "api",
    ghpagesNoJekyll := false,
    git.remoteRepo := {
      sys.env.get("GH_TOKEN") match {
        case None => "github.com:larsrh/libisabelle.git"
        case Some(token) => s"https://$token@github.com/larsrh/libisabelle.git"
      }
    },
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.yml" | "*.md" | "Gemfile" | "config",
    watchSources ++= (siteSourceDirectory.value ** "*").get,
    watchSources += ((baseDirectory in ThisBuild).value / "README.md"),
    siteMappings += ((baseDirectory in ThisBuild).value / "README.md", "_includes/README.md"),
    site.addMappingsToSiteDir(tut, "_tut")
  )

addCommandAlias("pushSite", "; docs/makeSite ; docs/ghpagesPushSite")

lazy val pideInterface = project.in(file("modules/pide-interface"))
  .settings(moduleName := "pide-interface")
  .settings(standardSettings)
  .settings(warningSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    buildInfoKeys := apiBuildInfoKeys,
    buildInfoPackage := "info.hupel.isabelle.api",
    libraryDependencies ++= Seq(
      "com.chuusai" %% "shapeless" % "2.3.2",
      "io.monix" %% "monix-execution" % "2.2.1",
      "org.log4s" %% "log4s" % "1.3.4"
    )
  )

lazy val libisabelle = project.in(file("modules/libisabelle"))
  .dependsOn(pideInterface)
  .enablePlugins(LibisabellePlugin)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(macroSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "0.9.0",
      "org.typelevel" %% "cats-free" % "0.9.0",
      "com.lihaoyi" %% "scalatags" % "0.6.2",
      "info.hupel" % "classy" % "0.1.4"
    ),
    libraryDependencies += {
      val version =
        if (scalaVersion.value.startsWith("2.10"))
          "0.5.0"
        else
          "0.8.0"
      "org.scala-lang.modules" %% "scala-java8-compat" % version
    },
    isabelleSessions in Compile := Seq(
      "Protocol",
      "HOL-Protocol"
    )
  )

lazy val setup = project.in(file("modules/setup"))
  .dependsOn(libisabelle, pideInterface)
  .settings(moduleName := "libisabelle-setup")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "1.0.0-M15-1",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M15-1",
      "org.apache.commons" % "commons-compress" % "1.13",
      "org.apache.commons" % "commons-lang3" % "3.5",
      "commons-io" % "commons-io" % "2.5"
    )
  )


// PIDE implementations

def pide(version: String) = Project(s"pide$version", file(s"modules/pide/$version"))
  .dependsOn(pideInterface % "provided")
  .settings(moduleName := s"pide-$version")
  .settings(standardSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    buildInfoKeys := apiBuildInfoKeys,
    buildInfoPackage := "info.hupel.isabelle.impl",
    autoScalaLibrary := false,
    libraryDependencies += "org.scala-lang" % "scala-library" % scalaVersion.value % "provided",
    libraryDependencies ++= {
      val dep =
        ("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.5").exclude("org.scala-lang", "scala-library")

      if (scalaVersion.value startsWith "2.10")
        Seq()
      else
        Seq(dep)
    },
    proguardSettings,
    ProguardKeys.options in Proguard ++= Seq(
      "-keep public class info.hupel.isabelle.impl.Environment { public protected private *; }",
      "-keep public class info.hupel.isabelle.impl.BuildInfo { public *; }",
      "-dontoptimize",
      "-dontobfuscate",
      "-dontwarn",
      "-dontnote",
      "-ignorewarnings"
    ),
    ProguardKeys.proguardVersion in Proguard := "5.3.2",
    ProguardKeys.outputs in Proguard := Seq(target.value / s"${moduleName.value}-assembly.jar")
  )

lazy val pideExtraSettings = Seq(
  libraryDependencies ++= Seq(
    "org.tukaani" % "xz" % "1.6",
    "com.jcraft" % "jsch" % "0.1.54",
    "com.jcraft" % "jzlib" % "1.1.3"
  )
)

lazy val pide2016 = pide("2016")
lazy val pide2016_1 = pide("2016-1").settings(pideExtraSettings)

def assemblyGenerator(p: Project) = Def.task {
  val Seq(source) = (ProguardKeys.proguard in Proguard in p).value
  val target = resourceManaged.value / source.getName
  val log = streams.value.log
  log.info(s"Copying assembly $source to $target ...")
  IO.copyFile(source, target, preserveLastModified = true)
  Seq(target)
}

lazy val pidePackage = project.in(file("modules/pide-package"))
  .dependsOn(pideInterface)
  .settings(moduleName := "pide-package")
  .settings(standardSettings)
  .settings(
    resourceGenerators in Compile ++= Seq(
      assemblyGenerator(pide2016).taskValue,
      assemblyGenerator(pide2016_1).taskValue
    )
  )


// Tests & CI

lazy val tests = project.in(file("tests"))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(offlineTest, pureTest, holTest)

val specs2Version = "3.8.7"

lazy val offlineTest = project.in(file("tests/offline"))
  .dependsOn(setup, pidePackage)
  .enablePlugins(LibisabellePlugin)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(loggingSettings)
  .settings(
    isabellePackage := "tests",
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % specs2Version,
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test"
    )
  )

lazy val pureTest = project.in(file("tests/pure"))
  .dependsOn(offlineTest)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    parallelExecution in Test := false
  )

lazy val holTest = project.in(file("tests/hol"))
  .dependsOn(offlineTest)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    logBuffered in Test := false,
    libraryDependencies += "org.specs2" %% "specs2-scalacheck" % specs2Version % "test"
  )

addCommandAlias("validateSlow", "; holTest/test")
addCommandAlias("validateQuick", "; offlineTest/test ; pureTest/test")


// Standalone applications

lazy val cli = project.in(file("modules/cli"))
  .dependsOn(setup, pidePackage)
  .settings(moduleName := "isabellectl")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(macroSettings)
  .settings(loggingSettings)
  .settings(
    libraryDependencies += "com.github.alexarchambault" %% "case-app" % "1.1.3",
    mainClass in Compile := Some("info.hupel.isabelle.cli.Main"),
    assemblyJarName in assembly := s"isabellectl-assembly-${version.value}",
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(defaultShellScript))
  )
  .enablePlugins(LibisabellePlugin)

TaskKey[File]("script") := {
  val executable = (assembly in cli).value.getCanonicalPath()
  val script = (baseDirectory in ThisBuild).value / "isabellectl"
  val text = s"""
    |#!/usr/bin/env bash
    |
    |exec "$executable" "$$@"
    |""".stripMargin.trim
  streams.value.log.info(s"Writing script to $script ...")
  IO.write(script, text)
  script.setExecutable(true)
  script
}


// Examples

lazy val examples = project.in(file("modules/examples"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    // logback because Java
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.1.9"
  )


// Workbench

lazy val workbench = project.in(file("modules/workbench"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(loggingSettings)
  .settings(
    initialCommands in console := """
      import io.rbricks.scalog.{Level, LoggingBackend}
      LoggingBackend.console("info.hupel" -> Level.Trace)

      import info.hupel.isabelle._
      import info.hupel.isabelle.api._
      import info.hupel.isabelle.hol._
      import info.hupel.isabelle.pure._
      import info.hupel.isabelle.setup._
      import scala.concurrent.duration.Duration
      import scala.concurrent.Await
      import monix.execution.Scheduler.Implicits.global

      val setup = Setup.default(Version("2016-1")).right.get
      val resources = Resources.dumpIsabelleResources().right.get
      val config = Configuration.simple("HOL-Protocol")
      val env = Await.result(setup.makeEnvironment(resources), Duration.Inf)
      System.build(env, config)
      val system = Await.result(System.create(env, config), Duration.Inf)

      val main = Theory.get("Protocol_Main")
      val ctxt = Context.initGlobal(main)
      """,
    cleanupCommands in console := """
      system.dispose
      """,
    sourceGenerators in Compile += Def.task {
      val contents = s"""object Test {
        ${(initialCommands in console).value}
        ${(cleanupCommands in console).value}
      }"""
      val file = (sourceManaged in Test).value / "test.scala"
      IO.write(file, contents)
      Seq(file)
    }.taskValue
  )


// Release stuff

import ReleaseTransformations._

releaseVcsSign := true
releaseCrossBuild := true

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
  ReleaseStep(action = Command.process("sonatypeRelease", _))
)


// Miscellaneous

cancelable in Global := true
