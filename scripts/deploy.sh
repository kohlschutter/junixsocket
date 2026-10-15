#!/usr/bin/env zsh
#
# Deploys code to Maven Central
# 
# Copyright 2026 by Christian Kohlschütter
# SPDX-License-Identifier: Apache-2.0
#

set -e
dirname=$(dirname "$0")
cd "${dirname}/.."

mvn clean deploy -Drelease -Ddeploy -Dignorant -Dcentral -DskipTests -Dsigned -Dgpgkeyname=$(git config --get user.email) -Dgpg.executable=$(which gpg)
