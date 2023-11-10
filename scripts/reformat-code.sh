#!/bin/sh
#
# Reformats the Java code in this project
# 
# Copyright 2023 by Christian Kohlschütter
# SPDX-License-Identifier: Apache-2.0
#

cd "$(dirname $0)"/..
mvn process-sources -Dreformat -Dignorant -pl \!junixsocket-native,\!junixsocket-native-cross,\!junixsocket-native-common,\!junixsocket-native-custom
