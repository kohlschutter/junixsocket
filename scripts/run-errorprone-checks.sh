#!/usr/bin/env zsh
#
# Runs "google errorprone" checks on this Maven project
# 
# Copyright 2023 by Christian Kohlschütter
# SPDX-License-Identifier: Apache-2.0
#

set -e
dirname=$(dirname "$0")
cd "${dirname}/.."

opts=()
if [[ $#@ -eq 0 ]]; then
	opts=( -rf :junixsocket-common )
else
	opts=( $@ )
fi

out=()

hasError=0
while read l; do
	#echo $l;
	case "$l" in
		"[INFO]"*)
			;;
		"[WARNING] To suppress warnings about obsolete options"*)
			;;
		"[WARNING]"*)
			echo
			echo $l;
			;;
		"--")
			;;
		"[ERROR]"*)
			hasError=1
			echo $l
			;;
		*)
			echo $l
			;;
	esac
done < <(
set +e
MAVEN_OPTS="--add-exports jdk.compiler/com.sun.tools.javac.api=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.file=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.main=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.model=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.parser=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.processing=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.tree=ALL-UNNAMED --add-exports jdk.compiler/com.sun.tools.javac.util=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.code=ALL-UNNAMED --add-opens jdk.compiler/com.sun.tools.javac.comp=ALL-UNNAMED" mvn clean compile ${opts[@]} -Derrorprone -P \!multirelease,\!multirelease-java9,\!multirelease-java10,\!multirelease-java11,\!multirelease-java12,\!multirelease-java13,\!multirelease-java14,\!multirelease-java15,\!multirelease-java16,\!multirelease-java17,\!multirelease-java18,\!multirelease-java19,\!multirelease-java20,\!multirelease-java21,\!multirelease-java22 | grep -i -C2 error
#prone.info
)
echo errorprone RC=$hasError
exit $hasError
