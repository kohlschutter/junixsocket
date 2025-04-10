#!/bin/sh
#
# Reformats the Java code in this project
# 
# Copyright 2023-2025 by Christian Kohlschütter
# SPDX-License-Identifier: Apache-2.0
#

set -e
dirname=$(dirname "$0")
cd "${dirname}/.."

if [[ "$1" != "-pl" ]]; then
  mvn process-sources -Dreformat -Dignorant -pl \!junixsocket-native,\!junixsocket-native-cross,\!junixsocket-native-common,\!junixsocket-native-custom $@
else
  mvn process-sources -Dreformat -Dignorant $@
fi
