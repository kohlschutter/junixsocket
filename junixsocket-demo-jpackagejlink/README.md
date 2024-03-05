# junixsocket-demo-jpackagejlink

Example code to demonstrate how to use junixsocket with jlink and jpackage.

## Definitions

### jlink

jlink builds Java runtime images that are tailored towards a specific set of Java classes / modules.

jlink was introduced in Java 9.

### jpackage

jpackage builds native installers for applications, utilizing optimized runtimes very much like jlink.

jpackage was introduced in Java 14.

## The demo code

This demo simply runs a very simple selftest built into junixsocket-common.

It depends on `junixsocket-core`, which is a POM-only dependency that in turn references
`junixsocket-common` (the common junixsocket API) and `junixsocket-native-common` (the
native library images for common operating systems).

## Build instructions

The main purpose of this Maven module is to demonstrate how to configure a project's `pom.xml` file
such that jlink/jpackage-compatible artifacts can be created.
 
By default, this demo does not create executable jlink/jpackage artifacts.
You have to invoke the `mvn` command with a given profile setting to enable them:

    cd junixsocket-demo-jpackagejlink
    mvn clean verify -Djpackage -Djlink 

Omit either `-Djpackage` or `-Djlink` if desired.

See the [junixsocket-demo-jpackagejlink POM file](pom.xml) for how this is done.

## Results

See `junixsocket-demo-jpackagejlink/target/jpackage` for the binary package result (the actual file
depends on your target platform).

See `junixsocket-demo-jpackagejlink/target/jlink/image` for the Java runtime optimized for the demo
code.
    
## References

* [Tools Reference: jlink](https://docs.oracle.com/en/java/javase/11/tools/jlink.html)
* [Tools Reference: jpackage](https://docs.oracle.com/en/java/javase/14/docs/specs/man/jpackage.html)
