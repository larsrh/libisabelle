#!/bin/bash

set -x

git config --global user.name "Lars Hupel"
git config --global user.email "lars.hupel@mytum.de"
./sbt "++$SCALA_VERSION" pushSite
