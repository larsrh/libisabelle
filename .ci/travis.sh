#!/bin/bash

set -ex

curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > sbt
chmod 0755 sbt

./sbt "++$SCALA_VERSION" compile
./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol build"
./sbt "++$SCALA_VERSION" validateQuick

if [ "$PROFILE" = "slow" ]; then
  ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session HOL-Protocol build"
  ./sbt "++$SCALA_VERSION" validateSlow
  ./sbt "++$SCALA_VERSION" "cli/assembly"
  if [ "$DEPLOY" = "1" ]; then
    ./sbt "++$SCALA_VERSION" tut
  fi
else
  ./sbt "++$SCALA_VERSION" publishLocal
fi
