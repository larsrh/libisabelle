---
layout: default
title: Setup
---

# Setup

To get started with the repository, follow these steps:

1. [Install sbt](http://www.scala-sbt.org/download.html).
2. Start `sbt` to fetch all required Scala dependencies.
   After this is done, you are in the SBT shell.
3. Compile the sources with `compile`.
4. Bootstrap an Isabelle installation using `cli/run --version 2016 build`, which will download and extract the latest supported Isabelle version for you.
   Additionally, it will build a default Isabelle session.

On some systems, you might need to install Perl, Python, and/or some additional libraries.

Note to proficient Isabelle users:
`libisabelle` does not respect `ISABELLE_HOME`.
Bootstrapping will create a new installation in your home folder (Linux: `~/.local/share`, Windows: `%LOCALAPPDATA%`, OS X: `~/Library/Preferences`).

## Validating your setup

In the SBT shell, run:

```
> cli/run --version 2016 check
```

If you see some log output containing `Alive!`, then your installation is working correctly.

## Operating system support

`libisabelle` works on all platforms supported by Isabelle (Mac OS X, Linux, Windows).
It can either use an existing installation or bootstrap one (by downloading the appropriate archive from the Isabelle website).
Bootstrapping is automatically tested on all three platforms.

_Note on Windows support:_
AppVeyor builds `libisabelle` on Windows, but only bootstraps an Isabelle installation.
It does not run the integration tests (for now).

## Running the tests

Run the `sbt` script again, then, in the SBT shell, type `test`.
This requires the environment variable `ISABELLE_VERSION` to be set.
Another option is to pass the version to the test framework directly.

Example:

```
$ cd libisabelle
$ ./sbt
> cli/run --version 2016 --session Protocol --internal build
> cli/run --version 2016 --session HOL-Protocol --internal build
> testOnly * -- isabelle.version 2016
```

or:

```
$ cd libisabelle
$ ISABELLE_VERSION=2016 ./sbt
> cli/run --version 2016 --session Protocol --internal build
> cli/run --version 2016 --session HOL-Protocol --internal build
> test
```

Make sure to have bootstrapped the installation as described above for the appropriate Isabelle version, otherwise the tests will fail.
Also note that the tests require the extra sessions `Protocol` and `HOL-Protocol` to be built (as shown in the examples above).
You only have to do that once after cloning or updating Isabelle code.
