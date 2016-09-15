#!/bin/bash

set -x

./sbt "++$SCALA_VERSION" compile
./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol build"
./sbt "++$SCALA_VERSION" validateQuick

if [ "$PROFILE" = "slow" ]; then
  ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session HOL-Protocol build"
  ./sbt "++$SCALA_VERSION" validateSlow
  ./sbt "++$SCALA_VERSION" "cli/assembly"
else
  ./sbt "++$SCALA_VERSION" publishLocal
fi
