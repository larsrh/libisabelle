#!/bin/bash

set -e

openssl aes-256-cbc -K $encrypted_92e46eb875dd_key -iv $encrypted_92e46eb875dd_iv -in travis-deploy-key.enc -out travis-deploy-key -d
chmod 600 travis-deploy-key
cp travis-deploy-key ~/.ssh/id_rsa
git config --global user.name "Lars Hupel"
git config --global user.email "lars.hupel@mytum.de"
./sbt "++$SCALA_VERSION" pushSite
