#!/bin/bash

set -e

# <https://coderwall.com/p/v_fh4w/easy-bash-color-output>
black=0; red=1; green=2; yellow=3; blue=4; pink=5; cyan=6; white=7;
cecho() {
  local _color=$1; shift
  echo -e "$(tput setaf $_color)$(tput bold)$@$(tput sgr0)"
}

die() {
  cecho $red "$1" >&2
  exit 1
}

ask() {
  local answer
  if [ -n "$2" ]; then
    echo -n "$1 [default: $2] " >&2
  else
    echo -n "$1 " >&2
  fi
  read answer
  if [ -n "$2" ]; then
    echo "${answer:-$2}"
  elif [ -n "$answer" ]; then
    echo "$answer"
  else
    die "No answer provided"
  fi
}

GIT_ROOT="$(git rev-parse --show-toplevel)"
FILE="$GIT_ROOT/.travis.yml"

cecho $white "Generating .travis.yml in $GIT_ROOT"

if [ -f "$FILE" ]; then
  die ".travis.yml already exists" 
fi

echo

VERSION="$(ask "Isabelle version?" "2016-1")"

ENV=$(ask "Environment? [c (low memory, fast boot, 32 bit)/s (high memory, slow boot, 64 bit)]" "c")

case "$ENV" in
  c)
    TRAVIS_SUDO="false"
    TRAVIS_ADDONS='addons: { apt: { packages: ["lib32stdc++6"] } }'
    ;;
  s)
    TRAVIS_SUDO="required"
    TRAVIS_ADDONS=""
    ;;
  *)
    die "Invalid answer"
esac

AFP=$(ask "Include AFP? [y/n]" "y")

case "$AFP" in
  y)
    AFP="--afp"
    ;;
  n)
    AFP=""
    ;;
  *)
    die "Invalid answer"
esac

SESSION=$(ask "Session to build?")

CONTENTS=$(cat <<EOF
language: java
jdk: oraclejdk8

sudo: $TRAVIS_SUDO

$TRAVIS_ADDONS

script:
  - curl -Ls -o isabellectl https://dl.bintray.com/larsrh/libisabelle/nightly/isabellectl
  - chmod +x isabellectl
  - ./isabellectl --session "$SESSION" --version "$VERSION" $AFP --include . --verbose build

cache:
  directories:
    - \$HOME/.local/share/libisabelle

EOF
)

echo
cecho $green "Writing the following content:"
echo
echo "$CONTENTS"
echo
echo "$CONTENTS" > "$FILE"
cecho $green "Done"
