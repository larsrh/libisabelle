# libisabelle

A Scala library which talks to Isabelle.
It currently works with Isabelle2016, 2016-1 and 2017.
For more information and documentation about the project, visit [its website](http://lars.hupel.info/libisabelle/).

| Service                   | Status |
| ------------------------- | ------ |
| Travis (Linux/Mac CI)     | [![Build Status](https://travis-ci.org/larsrh/libisabelle.svg?branch=master)](https://travis-ci.org/larsrh/libisabelle) |
| AppVeyor (Windows CI)     | [![Build status](https://ci.appveyor.com/api/projects/status/uuafgv21ragvoqei/branch/master?svg=true)](https://ci.appveyor.com/project/larsrh/libisabelle/branch/master) |
| Scaladex                  | [![Latest release](https://index.scala-lang.org/larsrh/libisabelle/libisabelle/latest.svg?color=orange)](https://index.scala-lang.org/larsrh/libisabelle) |
| Scaladoc (release)        | [![Scaladoc](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.12.svg?label=scaladoc)](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.12) |
| Zenodo (DOI)              | [![DOI](https://zenodo.org/badge/DOI/10.5281/zenodo.591695.svg)](https://doi.org/10.5281/zenodo.591695) |
| Gitter (Chat)             | [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/larsrh/libisabelle) |


## Including libisabelle into your project

`libisabelle` is cross-built for Scala 2.11.x and 2.12.x.
Drop the following lines into your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "info.hupel" %% "libisabelle" % "0.9.2",
  "info.hupel" %% "libisabelle-setup" % "0.9.2"
)
```

With this configuration, the automatic Isabelle setup will fetch additional JAR dependencies from Maven Central or Sonatype, depending on the selected Isabelle version.
If you don't want this, additionally include the following dependency:

```scala
  "info.hupel" %% "pide-package" % "0.9.2"
```

This adds PIDE implementations for all supported Isabelle versions to your classpath.


## Using the plugin

There is an [sbt plugin](https://github.com/larsrh/sbt-libisabelle/) for working with Isabelle sources available.
Refer to its README for more information.
You should use the plugin if your build contains Isabelle source files.


## Command line interface

`libisabelle` features a CLI called `isabellectl`.
Currently, it only allows some basic features like downloading, building and launching Isabelle.
You can download the latest nightly CLI executable from [Bintray](https://dl.bintray.com/larsrh/libisabelle/nightly/isabellectl).


### Downloading & checking your installation

```
isabellectl check
```


### Launching Isabelle/jEdit

```
isabellectl -s HOL-Probability jedit
```


### Using Isabelle tools

```
isabellectl exec getenv ISABELLE_HOME
```


## Participation

This project supports the [Typelevel][typelevel] [code of conduct][codeofconduct] and wants all of its channels (at the moment: GitHub and Gitter) to be welcoming environments for everyone.

[typelevel]: http://typelevel.org/
[codeofconduct]: http://typelevel.org/conduct.html
