# libisabelle

A Scala library which talks to Isabelle.
It currently works with Isabelle2016 and Isabelle2016-1.
For more information and documentation about the project, visit [its website](http://lars.hupel.info/libisabelle/).

| Service                   | Status |
| ------------------------- | ------ |
| Travis (Linux/Mac CI)     | [![Build Status](https://travis-ci.org/larsrh/libisabelle.svg?branch=master)](https://travis-ci.org/larsrh/libisabelle) |
| AppVeyor (Windows CI)     | [![Build status](https://ci.appveyor.com/api/projects/status/uuafgv21ragvoqei/branch/master?svg=true)](https://ci.appveyor.com/project/larsrh/libisabelle/branch/master) |
| Scaladex                  | [![Latest release](https://index.scala-lang.org/larsrh/libisabelle/libisabelle/latest.svg?color=orange)](https://index.scala-lang.org/larsrh/libisabelle) |
| Scaladoc (release)        | [![Scaladoc](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.11.svg?label=scaladoc)](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.11) |
| Zenodo (DOI)              | [![DOI](https://zenodo.org/badge/3836/larsrh/libisabelle.svg)](https://zenodo.org/badge/latestdoi/3836/larsrh/libisabelle) |
| Gitter (Chat)             | [![Gitter](https://badges.gitter.im/Join%20Chat.svg)](https://gitter.im/larsrh/libisabelle) |


## Including libisabelle into your project

`libisabelle` is cross-built for Scala 2.10.x, 2.11.x, and 2.12.x.
Drop the following lines into your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "info.hupel" %% "libisabelle" % "0.6.4",
  "info.hupel" %% "libisabelle-setup" % "0.6.4"
)
```

With this configuration, the automatic Isabelle setup will fetch additional JAR dependencies from Maven Central or Sonatype, depending on the selected Isabelle version.
If you don't want this, additionally include the following dependency:

```scala
  "info.hupel" %% "pide-package" % "0.6.4"
```

This adds PIDE implementations for all supported Isabelle versions to your classpath.


## Command line interface

`libisabelle` features a CLI called `isabellectl`.
Currently, it only allows some basic features like downloading, building and launching Isabelle.
You can download the latest nightly CLI executable from [Bintray](https://dl.bintray.com/larsrh/libisabelle/nightly/isabellectl).


### Downloading & checking your installation

```
isabellectl --version 2016 check
```


### Launching Isabelle/jEdit

```
isabellectl --version 2016 --session HOL-Probability jedit
```


### Using Isabelle tools

```
isabellectl --version 2016 exec getenv ISABELLE_HOME
```


## Participation

This project supports the [Typelevel][typelevel] [code of conduct][codeofconduct] and wants all of its channels (at the moment: GitHub and Gitter) to be welcoming environments for everyone.

[typelevel]: http://typelevel.org/
[codeofconduct]: http://typelevel.org/conduct.html
