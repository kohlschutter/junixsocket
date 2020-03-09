#!/usr/bin/env bash

if [ $# -eq 0 ]; then
  echo "Syntax: $0 <*.jar|nar>+" >&2
  exit 1
fi

dir="$(dirname $0)"
re='^(junixsocket-native|junixsocket-native-custom)-([0-9\.]+(-SNAPSHOT)?)-([_A-Za-z0-9\-]+).(jar|nar)$'
for f in $@; do
  basename=$(basename "$f")
  pathname="$(cd "$(dirname "$f")"; pwd)/$basename"
  if [ ! -f "$pathname" ]; then
    echo "File does not exist: $f" >&2
    continue
  fi    

  if [[ ! "$basename" =~ $re ]]; then
    echo "Skipping unsupported file: $f" >&2
    continue
  fi
  artifact="${BASH_REMATCH[1]}"
  version="${BASH_REMATCH[2]}"
  classifier="${BASH_REMATCH[4]}"
  packaging="${BASH_REMATCH[5]}"

  newname="$artifact-$version-$classifier.$packaging"
  if [[ "$basename" != "$newname" ]]; then
    echo "Skipping invalid file: $f" >&2
    continue
  fi

  ( cd "$dir"; set -x ; mvn -pl "" install:install-file -Dfile="$pathname" -DgroupId=com.kohlschutter.junixsocket -DartifactId="$artifact" -Dversion="$version" -Dpackaging="$packaging" -Dclassifier="$classifier" )
done

echo
echo "Nar files available in local Maven repository cache:"
"$dir/list-available-in-m2repo.sh" "$version"
