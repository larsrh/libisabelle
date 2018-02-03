#!/bin/bash

set -ex

curl -s https://raw.githubusercontent.com/paulp/sbt-extras/master/sbt > sbt
chmod 0755 sbt

./sbt "++$SCALA_VERSION" compile

case "$PROFILE" in
  devel)
    ./sbt "++$SCALA_VERSION" script
    ./isabellectl --version "$ISABELLE_VERSION" --verbose exec components -- -I
    ./isabellectl --version "$ISABELLE_VERSION" --verbose exec components -- -a
    ./isabellectl --version "$ISABELLE_VERSION" --verbose exec jedit -- -bf
    ./isabellectl --version "$ISABELLE_VERSION" --session Pure --verbose build
    ;;
  generic)
    ./sbt "++$SCALA_VERSION" script
    ./isabellectl --version "$ISABELLE_VERSION" --session Pure --verbose build
    ;;
  quick)
    ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol --internal --verbose build"
    ./sbt "++$SCALA_VERSION" validateQuick
    ./sbt "++$SCALA_VERSION" publishLocal
    ;;
  slow)
    ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session Protocol --internal --verbose build"
    ./sbt "++$SCALA_VERSION" validateQuick
    ./sbt "++$SCALA_VERSION" "cli/run --version $ISABELLE_VERSION --session HOL-Protocol --internal --verbose build"
    ./sbt "++$SCALA_VERSION" validateSlow
    ;;
esac

if [ "$DEPLOY" = "1" ]; then
  ./sbt "++$SCALA_VERSION" tut
  ./sbt "cli/assembly"
  cp modules/cli/target/scala-2.12/isabellectl-assembly-* isabellectl-bin
fi
