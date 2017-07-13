#!/bin/bash

set -ex

curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > sbt
chmod 0755 sbt

./sbt "++$SCALA_VERSION" compile

if [ "$PROFILE" != "devel" ]; then
  ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol --internal --verbose build"
  ./sbt "++$SCALA_VERSION" validateQuick
else
  ./sbt "++$SCALA_VERSION" script
  ./isabellectl --version "$ISABELLE_VERSION" --verbose exec components -- -I
  ./isabellectl --version "$ISABELLE_VERSION" --verbose exec components -- -a
  ./isabellectl --version "$ISABELLE_VERSION" --session Pure --verbose build
fi

if [ "$PROFILE" = "slow" ]; then
  ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session HOL-Protocol --internal --verbose build"
  ./sbt "++$SCALA_VERSION" validateSlow
  ./sbt "++$SCALA_VERSION" "cli/assembly"
  if [ "$DEPLOY" = "1" ]; then
    ./sbt "++$SCALA_VERSION" tut
  fi
else
  ./sbt "++$SCALA_VERSION" publishLocal
fi
