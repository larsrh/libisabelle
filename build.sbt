import scala.sys.process._

lazy val defaultIsabelleVersions = settingKey[Seq[Version]]("Default Isabelle versions")
defaultIsabelleVersions in ThisBuild := Seq()

lazy val standardSettings = Seq(
  organization := "info.hupel",
  scalaVersion := "2.12.6",
  crossScalaVersions := Seq("2.11.12", "2.12.6"),
  javacOptions += "-Xlint:unchecked",
  homepage := Some(url("http://lars.hupel.info/libisabelle/")),
  licenses := Seq(
    "Apache-2.0" -> url("http://opensource.org/licenses/Apache-2.0"),
    "BSD" -> url("http://opensource.org/licenses/BSD-3-Clause")
  ),
  publishArtifact in Test := false,
  pomIncludeRepository := { _ => false },
  resolvers += Resolver.sonatypeRepo("public"),
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
  ),
  credentials += Credentials(
    Option(System.getProperty("build.publish.credentials")) map (new File(_)) getOrElse (Path.userHome / ".ivy2" / ".credentials")
  ),
  isabelleVersions := {
    isabelleVersions.?.value match {
      case Some(Seq()) => (defaultIsabelleVersions in ThisBuild).value
      case Some(ver) => ver
      case None => Nil
    }
  },
  autoAPIMappings := true
)

lazy val warningSettings = Seq(
  scalacOptions ++= Seq(
    "-deprecation",
    "-feature",
    "-encoding", "UTF-8",
    "-unchecked",
    "-Xlint",
    "-Ypartial-unification",
    "-Yno-adapted-args",
    "-Ywarn-dead-code",
    "-Ywarn-numeric-widen",
    "-Ywarn-value-discard",
    "-Xfatal-warnings"
  ),
  scalacOptions in (Compile, doc) ~= (_.filterNot(_ == "-Xfatal-warnings")),
  scalacOptions in (Compile, console) := Seq()
)

lazy val noPublishSettings = Seq(
  publish := (()),
  publishLocal := (()),
  publishArtifact := false
)

lazy val macroSettings = Seq(
  libraryDependencies ++= Seq(
    scalaOrganization.value % "scala-compiler" % scalaVersion.value % "provided",
    scalaOrganization.value % "scala-reflect" % scalaVersion.value % "provided",
    compilerPlugin("org.scalamacros" % "paradise" % "2.1.1" cross CrossVersion.patch)
  )
)

lazy val loggingSettings = Seq(
  libraryDependencies ++= Seq(
    "io.rbricks" %% "scalog-backend" % "0.2.1",
    "io.rbricks" %% "scalog-mdc" % "0.2.1",
    // scalac requires this pseudo-transitive dependency of scalog to be present,
    // even though we don't use its functionality here
    // this seems to be an issue for Scala <= 2.11.x
    "com.typesafe" % "config" % "1.3.3" % "provided"
  )
)

lazy val apiBuildInfoKeys = Seq[BuildInfoKey](
  version,
  scalaVersion,
  scalaBinaryVersion,
  organization,
  git.gitHeadCommit,
  BuildInfoKey.map(defaultIsabelleVersions) { case (k, v) =>
    (k, v collect { case Version.Stable(identifier) => identifier })
  }
)

lazy val root = project.in(file("."))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(
    pideInterface, libisabelle, setup,
    tests, docs, examples,
    cli,
    pideAggregate,
    pidePackage,
    workbench
  )

lazy val tutDirectory = settingKey[String]("tut directory")

lazy val docs = project.in(file("modules/docs"))
  .dependsOn(setup, pidePackage)
  .settings(moduleName := "libisabelle-docs")
  .settings(standardSettings)
  .settings(macroSettings)
  .settings(loggingSettings)
  .enablePlugins(TutPlugin, ScalaUnidocPlugin, SiteScaladocPlugin, GhpagesPlugin)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inProjects(pideInterface, libisabelle, setup),
    doc in Compile := (doc in ScalaUnidoc).value,
    scalacOptions in (ScalaUnidoc, unidoc) ++= Seq(
      "-doc-title", "libisabelle",
      "-doc-version", version.value,
      "-doc-source-url", s"https://github.com/larsrh/libisabelle/blob/${"git rev-parse HEAD" !!}€{FILE_PATH}.scala",
      "-sourcepath", (baseDirectory in ThisBuild).value.getAbsolutePath,
      "-Ypartial-unification"
    ),
    target in unidoc in ScalaUnidoc := crossTarget.value / "api",
    siteSubdirName in SiteScaladoc := "api/nightly",
    ghpagesNoJekyll := false,
    git.remoteRepo := "git@github.com:larsrh/libisabelle.git",
    includeFilter in makeSite := "*.html" | "*.css" | "*.png" | "*.jpg" | "*.yml" | "*.md" | "Gemfile" | "config",
    siteMappings += ((baseDirectory in ThisBuild).value / "README.md", "_includes/README.md"),
    tutDirectory := "_tut",
    // this seems to be required for scalog
    libraryDependencies += "com.typesafe" % "config" % "1.3.3"
  )
  .settings(
    addMappingsToSiteDir(tut, tutDirectory)
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
      "com.chuusai" %% "shapeless" % "2.3.3",
      "io.monix" %% "monix-execution" % "3.0.0-RC1",
      "org.log4s" %% "log4s" % "1.8.0",
      // the dependencies below are not strictly necessary for pide-interface,
      // but all non-generic PIDE implementations require them
      "org.scala-lang.modules" %% "scala-parser-combinators" % "1.1.1",
      "org.tukaani" % "xz" % "1.8",
      "com.jcraft" % "jsch" % "0.1.55",
      "com.jcraft" % "jzlib" % "1.1.3",
      "org.scala-lang" % "scala-compiler" % scalaVersion.value,
      "org.xerial" % "sqlite-jdbc" % "3.27.2.1"
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
      "org.typelevel" %% "cats-core" % "1.6.0",
      "org.typelevel" %% "cats-free" % "1.6.0",
      "com.lihaoyi" %% "scalatags" % "0.6.8",
      "org.apache.commons" % "commons-lang3" % "3.9",
      "info.hupel" % "classy" % "0.2.2",
      "org.scala-lang.modules" %% "scala-java8-compat" % "0.9.0"
    ),
    isabelleSessions in Compile := Seq(
      "Protocol",
      "HOL-Protocol"
    )
  )

lazy val setup = project.in(file("modules/setup"))
  .dependsOn(libisabelle, pideInterface)
  .enablePlugins(LibisabelleAssemblyPlugin)
  .settings(moduleName := "libisabelle-setup")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= Seq(
      "io.get-coursier" %% "coursier" % "1.0.3",
      "io.get-coursier" %% "coursier-cache" % "1.0.3",
      "org.apache.commons" % "commons-compress" % "1.18",
      "org.eclipse.jgit" % "org.eclipse.jgit" % "5.3.1.201904271842-r",
      "commons-io" % "commons-io" % "2.6"
    )
  )


// PIDE implementations

lazy val pideVersion = settingKey[Version]("PIDE version")

def pide(version: String) = Project(s"pide$version", file(s"modules/pide/$version"))
  .dependsOn(pideInterface % "provided")
  .settings(moduleName := s"pide-$version")
  .settings(standardSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(
    buildInfoKeys := apiBuildInfoKeys,
    pideVersion := Version.Stable(version),
    buildInfoPackage := "info.hupel.isabelle.impl",
    autoScalaLibrary := false,
    assemblyJarName := s"${moduleName.value}-assembly.jar"
  )

lazy val pide2017 = pide("2017")
lazy val pide2018 = pide("2018")
lazy val pide2019_RC4 = pide("2019-RC4")

lazy val pides = Seq(
  pide2017,
  pide2018,
  pide2019_RC4
)

inThisBuild(pides.map { p =>
  defaultIsabelleVersions += (pideVersion in p).value
})

lazy val pideAggregate = project.in(file("modules/pide-aggregate"))
  .settings(noPublishSettings)
  .settings(standardSettings)
  .aggregate(pides.map(p => p: ProjectReference): _*)

def assemblyGenerator(p: Project) = Def.task {
  val source = (assembly in p).value
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
    pides.map { p =>
      resourceGenerators in Compile += assemblyGenerator(p).taskValue
    }
  )


// Tests & CI

lazy val tests = project.in(file("tests"))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(offlineTest, pureTest, holTest)

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
    libraryDependencies += "org.specs2" %% "specs2-scalacheck" % "4.5.1"
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
  .enablePlugins(LibisabellePlugin)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    logBuffered in Test := false,
    isabelleSessions in Test := Seq("HOL-Protocol-Test")
  )

addCommandAlias("validateSlow", "; holTest/test ; test:isabelleBuild")
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
    assemblyOption in assembly := (assemblyOption in assembly).value.copy(prependShellScript = Some(
      Seq("#!/usr/bin/env sh", """exec java -jar "$0" "$@"""" + "\n")
    ))
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
    libraryDependencies += "ch.qos.logback" % "logback-classic" % "1.2.3",
    jsr223Scripts in Compile += Script(
      "info.hupel.isabelle.examples.python",
      "Hello_PIDE",
      Language.Python,
      Script.Literal("import hello_pide")
    )
  )
  .enablePlugins(JSR223Plugin, JythonPlugin)


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

      val setup = Setup.default(Version.Stable("2016-1"), false).right.get
      val resources = Resources.dumpIsabelleResources().right.get
      val config = Configuration.simple("HOL-Protocol")
      val env = Await.result(setup.makeEnvironment(resources, Nil), Duration.Inf)
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
  publishArtifacts,
  setNextVersion,
  commitNextVersion,
  releaseStepCommand("sonatypeRelease")
)


// Miscellaneous

cancelable in Global := true
