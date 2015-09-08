import UnidocKeys._

lazy val standardSettings = Seq(
  organization := "info.hupel",
  scalaVersion := "2.11.7",
  crossScalaVersions := Seq("2.10.5", "2.11.7", "2.12.0-M2"),
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


lazy val root = project.in(file("."))
  .settings(standardSettings)
  .settings(noPublishSettings)
  .aggregate(pideInterface, libisabelle, setup, pide2014, pide2015, bootstrap, tests, docs)

lazy val docs = project.in(file("docs"))
  .settings(moduleName := "libisabelle-docs")
  .settings(standardSettings)
  .settings(unidocSettings)
  .settings(
    unidocProjectFilter in (ScalaUnidoc, unidoc) := inAnyProject -- inProjects(pide2014, pide2015, bootstrap, tests),
    doc in Compile := (doc in ScalaUnidoc).value,
    target in unidoc in ScalaUnidoc := crossTarget.value / "api"
  )

lazy val pideInterface = project.in(file("pide-interface"))
  .settings(moduleName := "pide-interface")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= {
      if (scalaVersion.value startsWith "2.10")
        Seq()
      else
        Seq("org.scala-lang.modules" %% "scala-parser-combinators" % "1.0.4")
    }
  )

lazy val libisabelle = project
  .dependsOn(pideInterface)
  .settings(standardSettings)
  .settings(warningSettings)
  .enablePlugins(GitVersioning, BuildInfoPlugin)
  .settings(Seq(
    buildInfoKeys := Seq[BuildInfoKey](version, scalaVersion, git.gitHeadCommit),
    buildInfoPackage := "edu.tum.cs.isabelle"
  ))

lazy val setup = project.in(file("setup"))
  .dependsOn(libisabelle, pideInterface)
  .settings(moduleName := "libisabelle-setup")
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.apache.commons" % "commons-compress" % "1.9",
      "org.apache.commons" % "commons-lang3" % "3.3.2",
      "com.github.fge" % "java7-fs-more" % "0.2.0",
      "com.google.code.findbugs" % "jsr305" % "1.3.+" % "compile"
    )
  )

lazy val pide2014 = project.in(file("pide/2014"))
  .dependsOn(pideInterface)
  .settings(moduleName := "pide-2014")
  .settings(standardSettings)

lazy val pide2015 = project.in(file("pide/2015"))
  .dependsOn(pideInterface)
  .settings(moduleName := "pide-2015")
  .settings(standardSettings)

lazy val versions = Map(
  "2014" -> pide2014,
  "2015" -> pide2015
)

lazy val bootstrap = project.in(file("bootstrap"))
  .dependsOn(libisabelle, setup, pideInterface)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .enablePlugins(BuildInfoPlugin)
  .settings(
    buildInfoPackage := "edu.tum.cs.isabelle.bootstrap",
    buildInfoKeys ++= {
      versions.toList.map { case (v, p) =>
        BuildInfoKey.map(classDirectory in (p, Compile)) {
          case (_, classFiles) =>
            (s"Isa$v", List(classFiles.toURI.toURL))
        }
      }
    }
  )

lazy val tests = project.in(file("tests"))
  .dependsOn(bootstrap)
  .settings(noPublishSettings)
  .settings(standardSettings)
  .settings(warningSettings)
  .settings(
    libraryDependencies ++= Seq(
      "org.specs2" %% "specs2-core" % "3.6.3" % "test",
      "org.specs2" %% "specs2-scalacheck" % "3.6.3" % "test",
      "org.scalacheck" %% "scalacheck" % "1.12.2" % "test"
    )
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
