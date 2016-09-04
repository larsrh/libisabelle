#!/bin/bash

set -x

./sbt "++$SCALA_VERSION" compile
./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol build"
./sbt "++$SCALA_VERSION" validateQuick

if [ "$TRAVIS_OS" = "linux" ]; then
  ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session HOL-Protocol build"
  ./sbt "++$SCALA_VERSION" validateSlow
else
  ./sbt "++$SCALA_VERSION" publishLocal
fi
