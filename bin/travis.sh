#!/usr/bin/env bash

set -xe

die() {
  echo "$1"
  exit 1
}

[ -z "$ISABELLE_VERSION" ] && die "No Isabelle version specified"
[ -z "$SCALA_VERSION" ]    && die "No Scala version specified"

./sbt "++$SCALA_VERSION" publishLocal
./sbt "++$SCALA_VERSION" "appBootstrap/run --version $ISABELLE_VERSION"
./sbt "++$SCALA_VERSION" test
