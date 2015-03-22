# libisabelle
Minimal wrapper around Isabelle/PIDE for non-IDE applications

## Setup

`libisabelle` is a Scala library which talks to Isabelle.
It currently works only with Isabelle2014 and does not automatically set up an appropriate Isabelle installation.

Follow these steps to set up your environment:

1. Obtain an Isabelle2014 archive from the [official website](http://isabelle.in.tum.de/).
2. Extract the archive to some arbitrary location. In there, you should find a folder `Isabelle2014`.
3. Set the environment variable `ISABELLE_HOME` to that folder.
4. In the `libisabelle` repository, build the `Protocol` session.

An example for Linux:

```bash
# extract to some arbitrary location
$ cd ~/bin
$ tar xzf ~/downloads/Isabelle2014_linux.tar.gz
# set environment variable
$ export ISABELLE_HOME=/home/lars/bin/Isabelle2014
# build required session
$ cd ~/libisabelle
$ $ISABELLE_HOME/bin/isabelle build -D . -bv
```

Instructions for other platforms can be found in the [installation guide](http://isabelle.in.tum.de/installation.html).
On some systems, you might need to install Perl, Python, and/or some additional libraries.
While `libisabelle` should be mostly platform-independent, currently, only Linux is tested.

## Running the tests

Run the `sbt` script in the root of this repository to start up the console of the build tool.
You don't need to have Scala installed.
On the first run, that script will automatically fetch all Java- and Scala-related dependencies.
Once the build tool is ready, type `test` to run the tests.

Example:

```
$ cd libisabelle
$ ./sbt
...
> test
```

Make sure to have `ISABELLE_HOME` set and the `Protocol` session built, otherwise the tests will fail.
