#!/bin/bash

set -x

SESSION="Protocol"

./sbt "++$SCALA_VERSION" compile
./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session $SESSION build"

if [ "$TRAVIS_OS" = "linux" ]; then
  ./sbt "++$SCALA_VERSION" validate
else
  ./sbt "++$SCALA_VERSION" validateQuick
fi
