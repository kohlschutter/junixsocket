#!/usr/bin/env bash
#
# junixsocket
#
# Copyright (c) 2009-2020 Christian Kohlschütter
#
# The author licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

useModulePath=0
printHelp=0

extraJars=()
while [ -n "$1" ]; do
  case "$1" in
   -m)
    echo "Using module path"
    useModulePath=1
    ;;
   -j)
    shift
    if [ -n "$1" ]; then
      jar="$1"
      extraJars+=("$jar")
    else
      echo "Missing parameter for -j" >&2
      exit 1
    fi
    ;;
   -h|--help)
     printHelp=1
     ;;
   --)
    shift
    break;
    ;;
   -*)
     echo "Illegal option: $1" >&2
     exit 1
    ;;
   *)
    break
    ;;
  esac
  shift
done

lastArg="${@: -1}"
if [[ "$lastArg" =~ '/^-/' || -z "$lastArg" ]]; then
  printHelp=1
fi

mysqlJar="$HOME/.m2/repository/mysql/mysql-connector-java/8.0.13/mysql-connector-java-8.0.13.jar"
if [ ! -f "$mysqlJar" ]; then
  mysqlJar="<path-to-mysql-connector-jar>"
fi
postgresqlJar="$HOME/.m2/repository/org/postgresql/postgresql/42.2.5/postgresql-42.2.5.jar"
if [ ! -f "$postgresqlJar" ]; then
  postgresqlJar="<path-to-postgresql-jar>"
fi
nanohttpdJar="$HOME/.m2/repository/org/nanohttpd/nanohttpd/2.3.1/nanohttpd-2.3.1.jar"
if [ ! -f "$nanohttpdJar" ]; then
  nanohttpdJar="<path-to-nanohttpd-jar>"
fi

if [[ $# -eq 0 || $printHelp -eq 1 ]]; then
  cat >&2 <<EOT
Syntax: $0 [-m] [-j jar]+ [-- [java opts]*] <classname>

Example:
# Runs the demo server
$0 org.newsclub.net.unix.demo.SimpleTestServer
# Runs the demo client
$0 org.newsclub.net.unix.demo.SimpleTestClient

# Runs the demo RMI server
$0 org.newsclub.net.unix.demo.rmi.SimpleRMIServer
# Runs the demo RMI client
$0 org.newsclub.net.unix.demo.rmi.SimpleRMIClient

# Runs the demo server. Replace "(demo)" with the desired demo.
$0 -- -Ddemo=(demo) org.newsclub.net.unix.demo.server.AFUNIXSocketServerDemo
# Runs the demo client. Replace "(demo)" with the desired demo, and "(socket)" with the socket to connect to.
$0 -- -Ddemo=(demo) -Dsocket=(socket) org.newsclub.net.unix.demo.client.DemoClient

# Runs the MySQL demo
$0 -j "$mysqlJar" -- -DmysqlSocket=/tmp/mysql.sock org.newsclub.net.mysql.demo.AFUNIXDatabaseSocketFactoryDemo

# Runs the PostgreSQL demo
$0 -j "$postgresqlJar" -- -DsocketPath=/tmp/.s.PGSQL.5432 org.newsclub.net.unix.demo.jdbc.PostgresDemo

# Runs the HTTP Server 
$0 -j "$nanohttpdJar" -- org.newsclub.net.unix.demo.nanohttpd.NanoHttpdServerDemo


Other flags:
 -m Use the Java module-path instead of the classpath (Java 9 or higher)
 -j <jar> Add the given jar to the beginning of the classpath/modulepath
 -- Separate the run-demo flags from the Java JVM flags

See also:
https://kohlschutter.github.io/junixsocket/demo.html
EOT
  exit 1
fi
libDir="$(dirname $0)/lib"

path="$libDir"$(for f in ${extraJars[@]} $(ls "$libDir"/*.jar); do echo -n ":$f"; done)
if [ $useModulePath -eq 1 ]; then
  java --module-path="$path" -Djdk.module.main=org.newsclub.net.unix.demo $@
else
  java -cp "$path" $@
fi
