#!/usr/bin/env zsh
#
# Runs "google errorprone" checks on this Maven project
# 
# Copyright 2023 by Christian Kohlschütter
# SPDX-License-Identifier: Apache-2.0
#

cd "$(dirname $0)"/..

opts=()
if [[ $#@ -eq 0 ]]; then
	opts=( -rf :junixsocket-common )
else
	opts=( $@ )
fi

out=()

while read l; do
	#echo $l;
	case "$l" in
		*ERROR*:*|*WARNING*:*)
			echo
			echo $l
			;;
		*errorprone.info*|*"Did you mean"*)
			echo $l
			;;
	esac
done < <(
MAVEN_OPTS="--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED" mvn clean compile ${opts[@]} -Perrorprone )
