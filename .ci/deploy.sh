#!/bin/bash

if [ "$TRAVIS_BRANCH" != "master" ]; then
  echo "This commit was made against the '$TRAVIS_BRANCH' branch and not 'master', no deploy."
  exit 0
fi

if [ "$TRAVIS_PULL_REQUEST" != "false" ]; then
  echo "This commit is in a pull request, no deploy."
  exit 0
fi

if [ "$PROFILE" = "slow" ]; then
  git config --global user.name "Lars Hupel"
  git config --global user.email "lars.hupel@mytum.de"
  ./sbt "++$SCALA_VERSION" pushSite
else
  echo "This build job executes the '$PROFILE' profile and not 'slow', no deploy."
fi
