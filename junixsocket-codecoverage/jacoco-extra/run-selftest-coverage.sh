#!/bin/sh

cd $(dirname "$0")

[[ -n "$JACOCO_VERSION" ]] || JACOCO_VERSION=0.8.10

if [[ ! -e "lib/jacocoagent.jar" ]]; then
  wget "https://search.maven.org/remotecontent?filepath=org/jacoco/jacoco/${JACOCO_VERSION}/jacoco-${JACOCO_VERSION}.zip" -O jacoco.zip

  unzip jacoco.zip lib/jacocoagent.jar
fi

mkdir -p ../target

java \
-javaagent:./lib/jacocoagent.jar=destfile=../target/$(uuidgen).exec,excludes='org/apache/maven/**:org/junit/**:org/apiguardian/**:sun/**' \
-jar ../../junixsocket-selftest/target/junixsocket-selftest-*-jar-with-dependencies.jar
