#!/usr/bin/env bash

classifierOnly=0
if [ "$1" == "-c" ]; then
  classifierOnly=1
  shift
fi

version="$1"
if [ -z "$version" ]; then
  echo "Syntax: $0 <junixsocket-version>" >&2
  exit 1
fi
m2dir="$HOME/.m2/repository/com/kohlschutter/junixsocket/junixsocket-native/$version/"
if [ ! -d "$m2dir" ]; then
  echo "No such directory: $m2dir" >&2
  exit 1
fi

cd "$m2dir"

if [ "$classifierOnly" -eq 1 ]; then
  re='^junixsocket-native-([0-9\.]+(-SNAPSHOT)?)-([_A-Za-z0-9\-]+).nar$'

  classifiers=()
  for nar in $(ls -1 "junixsocket-native-$version-"*".nar"); do
    if [[ ! "$nar" =~ $re ]]; then
      echo "Skipping unsupported file: $nar" >&2
      continue
    fi
    classifiers+=("${BASH_REMATCH[3]}")
  done
  for cl in $(echo ${classifiers[@]}| tr ' ' '\n' | sort -u); do
    echo $cl
  done
else
  ls -1 "junixsocket-native-$version-"*".nar"
fi
