import GhPagesKeys._
import SiteKeys._
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

lazy val docs = project.in(file("modules/docs"))
  .dependsOn(setup, pidePackage)
  .settings(moduleName := "libisabelle-docs")
  .settings(standardSettings)
  .settings(unidocSettings)
  .settings(macroSettings)
  .settings(tutSettings)
  .settings(site.settings ++ site.includeScaladoc("api/nightly"))
  .settings(ghpages.settings)
  .settings(
    libraryDependencies += logback,
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
      "org.log4s" %% "log4s" % "1.3.0"
    )
  )

lazy val libisabelle = project.in(file("modules/libisabelle"))
  .dependsOn(pideInterface)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(macroSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.typelevel" %% "cats-core" % "0.7.2",
      "org.typelevel" %% "cats-free" % "0.7.2",
      "io.monix" %% "monix-execution" % "2.0.1",
      "com.lihaoyi" %% "scalatags" % "0.6.0"
    )
  )

lazy val setup = project.in(file("modules/setup"))
  .dependsOn(libisabelle, pideInterface)
  .settings(moduleName := "libisabelle-setup")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "1.0.0-M14",
      "io.get-coursier" %% "coursier-cache" % "1.0.0-M14",
      "com.github.fge" % "java7-fs-more" % "0.2.0",
      "com.google.code.findbugs" % "jsr305" % "3.0.1" % "compile",
      "org.apache.commons" % "commons-compress" % "1.12",
      "org.apache.commons" % "commons-lang3" % "3.4"
    )
  )


// PIDE implementations

def pide(version: String) = Project(s"pide$version", file(s"modules/pide/$version"))
  .dependsOn(pideInterface % "provided")
  .settings(moduleName := s"pide-$version")
  .settings(standardSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(Seq(
    buildInfoKeys := apiBuildInfoKeys,
    buildInfoPackage := "info.hupel.isabelle.impl",
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

lazy val pidePackage = project.in(file("modules/pide-package"))
  .dependsOn(pideInterface)
  .settings(moduleName := "pide-package")
  .settings(standardSettings)
  .settings(
    resourceGenerators in Compile <+= assemblyGenerator(pide2015),
    resourceGenerators in Compile <+= assemblyGenerator(pide2016)
  )


// Tests & CI

lazy val tests = project.in(file("tests"))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(offlineTest, pureTest, holTest)

val specs2Version = "3.8.5"

lazy val offlineTest = project.in(file("tests/offline"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    isabellePackage := "tests",
    parallelExecution in Test := false,
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % specs2Version,
      "org.specs2" %% "specs2-scalacheck" % specs2Version % "test",
      logback
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
  .settings(
    libraryDependencies += logback,
    mainClass in Compile := Some("info.hupel.isabelle.cli.Main"),
    assemblyJarName in assembly := s"isabellectl-assembly-${version.value}.jar"
  )
  .enablePlugins(JavaAppPackaging, UniversalPlugin)


// Examples

lazy val examples = project.in(file("modules/examples"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(libraryDependencies += logback)


// Workbench

lazy val workbench = project.in(file("modules/workbench"))
  .dependsOn(setup, pidePackage)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(
    libraryDependencies += logback,
    initialCommands in console := """
      import info.hupel.isabelle._
      import info.hupel.isabelle.api._
      import info.hupel.isabelle.hol._
      import info.hupel.isabelle.pure._
      import info.hupel.isabelle.setup._
      import scala.concurrent.duration.Duration
      import scala.concurrent.Await
      import scala.concurrent.ExecutionContext.Implicits.global

      val setup = Await.result(Setup.defaultSetup(Version("2016")).toOption.get, Duration.Inf)
      val env = Await.result(setup.makeEnvironment, Duration.Inf)
      val resources = Resources.dumpIsabelleResources().toOption.get
      val config = resources.makeConfiguration(Nil, "HOL-Protocol")
      System.build(env, config)
      val system = Await.result(System.create(env, config), Duration.Inf)

      val main = Theory.get("Protocol_Main")
      val ctxt = Context.initGlobal(main)"""
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
