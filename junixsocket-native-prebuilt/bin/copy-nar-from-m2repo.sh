#!/usr/bin/env bash

version="$1"
outdir="$2"
if [ -z "$version" ]; then
  echo "Syntax: $0 <junixsocket-version> [outdir]" >&2
  exit 1
fi

m2dir="$HOME/.m2/repository/com/kohlschutter/junixsocket/junixsocket-native/$version/"
if [ ! -d "$m2dir" ]; then
  echo "Not a directory: $m2dir" >&2
  exit 1
fi

if [ -z "$outdir" ]; then
  outdir="$(dirname $0)/../nar"
fi
if [ ! -d "$outdir" ]; then
  echo "Target directory doesn't exist: $outdir" >&2
  exit 1
fi

cp -av "$m2dir"/"junixsocket-native-$version-"*".nar" "$outdir"/
