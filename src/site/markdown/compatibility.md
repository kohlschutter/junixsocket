# Compatibility Considerations

## Versioning

junixsocket versions consist of three parts: major, minor and patch (for example, 2.1.2).

`-SNAPSHOT` builds are not considered releases, but merely previews of a future release.

The native JNI library is expected to remain binary compatible for at least the current minor
version.

### junixsocket 2.1

junixsocket 2.1 is compatible with Java 8 and newer (tested up to Java 11).

It has been tested with Oracle's Java 8 JDK and OpenJDK for newer versions.

## Supported Architectures

The minimum set of supported platforms and processor architectures currently is macOS and Linux x86_64.

The native-common binaries are built on recent release versions of either platform.  

Support for [custom architectures](customarch.html) can be added by compiling a custom native binary
on the target machine. 

## Future versions

Future junixsocket major releases (e.g., 3.x, 4.x) may require newer versions of Java, most likely
aligned with the Java LTS (Long Term Support) releases.

A future junixsocket 3.x branch will support Java 11 or newer.

Critical bug fixes will be incorporated at least in every previous major release
(e.g., into 2.x when 3.0 comes out).
