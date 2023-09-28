# Build instructions

Details see `src/site/markdown/building.md` or [online here](https://kohlschutter.github.io/junixsocket/building.html)

## TL;DR. I just want to recompile some Java classes

To get started with building from source, you really only need:

- Java 17 or newer
- Maven 3.8.8 or newer
- clang (for the native library). Note that building the library doesn't work from Windows (use WSL2 with Linux).

Then try one the following commands:

##### Build everything, but only for the current architecture (not a full release build)

    mvn clean install

##### Build everything, except for the native library (recommended)

    mvn clean install -rf :junixsocket-common

#### Build everything, except for the native library and Java 7 support

    mvn clean install -Dretrolambda=false -rf :junixsocket-common

#### ... and I really don't care about code quality, just build really quick

    mvn clean install -Dretrolambda=false -rf :junixsocket-common -Dignorant

#### ... nor do I care about test results

    mvn clean install -Dretrolambda=false -rf :junixsocket-common -Dignorant -DskipTests
