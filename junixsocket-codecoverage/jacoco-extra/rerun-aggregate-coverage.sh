#!/bin/sh

cd "$(dirname $0)"/../..
mvn verify -Pstrict -Prelease -rf :junixsocket-common -P \!code-quality,\!documentation -DskipTests
