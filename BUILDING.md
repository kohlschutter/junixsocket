# Build instructions

Details see `src/site/markdown/building.md` or [online here](https://kohlschutter.github.io/junixsocket/building.html)

## TL;DR. I just want to recompile some Java classes

To get started with building from source, you really only need:

- Java 16 or newer
- Maven 3.8.8 or newer

Then try one the following commands:

##### Build everything, but only for the current architecture (not a full release build)

    mvn clean install

##### Build everything, except for the native library

    mvn clean install -rf :junixsocket-common

#### Build everything, except for the native library and Java 7 support

    mvn clean install -P '!retrolambda' -rf :junixsocket-common

#### I really don't care about code quality, just build really quick

    mvn clean install -P '!retrolambda' -rf :junixsocket-common -Dignorant

#### I really don't care about code quality or test results, just build really quick

    mvn clean install -P '!retrolambda' -rf :junixsocket-common -Dignorant -DskipTests
