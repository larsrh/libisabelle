# libisabelle
Minimal wrapper around Isabelle/PIDE for non-IDE applications

[![Build Status](https://travis-ci.org/larsrh/libisabelle.svg?branch=master)](https://travis-ci.org/larsrh/libisabelle)

## Setup

`libisabelle` is a Scala library which talks to Isabelle.
It currently works only with Isabelle2014 and Linux.

To get started, follow these steps:

1. Run the `sbt` script to fetch all required Scala dependencies.
   After this is done, you are in the SBT shell.
2. Type `bootstrap/run`, which will download and extract the latest supported Isabelle verison for you.

On some systems, you might need to install Perl, Python, and/or some additional libraries.

Note to previous Isabelle users:
`libisabelle` does not respect `ISABELLE_HOME` by default.
Bootstrapping will create a new installation in the `contrib` folder.

## Running the tests

Run the `sbt` script again, then, in the SBT shell, type `test`.
Once the build tool is ready, type `test` to run the tests.

Example:

```
$ cd libisabelle
$ ./sbt
...
> test
```

Make sure to have bootstrapped the installation as described above, otherwise the tests will fail.
