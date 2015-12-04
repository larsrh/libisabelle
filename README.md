# libisabelle
Minimal wrapper around Isabelle/PIDE for non-IDE applications

| Service                   | Status |
| ------------------------- | ------ |
| Travis (Linux CI)         | [![Build Status](https://travis-ci.org/larsrh/libisabelle.svg?branch=master)](https://travis-ci.org/larsrh/libisabelle) |
| Maven Central             | [![Maven Central](https://img.shields.io/maven-central/v/info.hupel/libisabelle_2.11.svg?label=latest%20release%20for%202.11)](https://search.maven.org/#search%7Cga%7C1%7Cg%3A%22info.hupel%22%20AND%20a%3A%22libisabelle_2.11%22) |
| Scaladoc                  | [![Scaladoc](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.11.svg?label=scaladoc)](http://javadoc-badge.appspot.com/info.hupel/libisabelle-docs_2.11) |

## Setup

`libisabelle` is a Scala library which talks to Isabelle.
It currently works with Isabelle2014 and Isabelle2015.

To get started, follow these steps:

1. Run the `sbt` script to fetch all required Scala dependencies.
   After this is done, you are in the SBT shell.
2. Compile the sources with `compile`.
3. If you have used an arbitrary snapshot of the sources (e.g. via `git clone`), run `publishLocal`.
4. Bootstrap an Isabelle installation using `appBootstrap/run --version 2015`, which will download and extract the latest supported Isabelle version for you.

On some systems, you might need to install Perl, Python, and/or some additional libraries.

Note to proficient Isabelle users:
`libisabelle` does not respect `ISABELLE_HOME`.
Bootstrapping will create a new installation in your home folder (Linux: `.local/share`, Windows: `%LOCALAPPDATA%`).

## Operating system support

Using an existing Isabelle installation, `libisabelle` should work on all platforms supported by Isabelle (Mac OS X, Linux, Windows).
Bootstrapping an Isabelle installation from within `libisabelle` should work on both Windows and Linux.
However, only Linux is tested regularly via continuous integration builds.


## Running the tests

Run the `sbt` script again, then, in the SBT shell, type `test`.
This requires the environment variable `ISABELLE_VERSION` to be set.
Another option is to pass the version to the test framework directly.

Example:

```
$ cd libisabelle
$ ./sbt
...
> testOnly * -- isabelle.version 2015
```

Make sure to have bootstrapped the installation as described above for the appropriate Isabelle version, otherwise the tests will fail.

## Including libisabelle into your project

`libisabelle` is cross-built for Scala 2.10.x and 2.11.x.
Drop the following lines into your `build.sbt`:

```scala
libraryDependencies ++= Seq(
  "info.hupel" %% "libisabelle" % "0.2",
  "info.hupel" %% "libisabelle-setup" % "0.2",
  "info.hupel" %% "pide-interface" % "0.2"
)
```
