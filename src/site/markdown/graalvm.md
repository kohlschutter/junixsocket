# GraalVM support

junixsocket supports running in [GraalVM](https://www.graalvm.org), both in Hotspot/OpenJDK-mode as
well as in [Native Image](https://www.graalvm.org/22.2/reference-manual/native-image/) mode with
Substrate VM (ahead-of-time compilation).

Some optional features, such as `junixsocket-rmi`, are currently unavailable in Native Image mode.

Support has been tested with GraalVM 22.1.0.

## Hotspot VM mode

In this mode, GraalVM behaves mostly like OpenJDK.

## Native Image/Substrate VM mode

In this mode, GraalVM attempts ahead-of-time compilation of our code, resulting in an executable
binary of your entire Java application.

To achieve this with junixsocket, GraalVM needs some specific "[Reachability
Metadata](https://www.graalvm.org/22.2/reference-manual/native-image/metadata/)".  Since junixsocket
2.6.0, this metadata is included with `junixsocket-common` and `junixsocket-selftest` artifacts.

## junixsocket selftest Native Image

You can try junixsocket's Native Image integration by running its selftests natively.

### Prerequisites

#### Make sure GraalVM is enabled

For example:

	# export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java17-22.2.0/Contents/Home
	# export PATH=$JAVA_HOME/bin:$PATH

#### Make sure your build system works

```
    ## Add dependencies if necessary, e.g.:
	sudo apt-get install gcc zlib1g-dev
```

#### Build the native image
```
# Build the platform-native executable:
cd junixsocket/junixsocket-selftest-native-image
mvn -Pnative clean package

# Run the platform-native executable:
./target/junixsocket-selftest-native-image-X.Y.Z
```

> **NOTE:** (Replace X.Y.Z with the actual version)

#### musl / Alpine Linux compatibility

The above binary, even though it currently cannot be built on Alpine Linux (musl), will run after
adding `gcompat`:

	sudo apk add gcompat

## Building and Maintaining junixsocket's Reachability Metadata

The required GraalVM metadata is currently obtained by running junixsocket's selftest with GraalVM's native-image-agent, and then manually separated into configurations that are relevant for `junixsocket-common`, and those specifically for `junixsocket-selftest`.

A shell script that exercises this path is provided (tested on macOS):

```
# Make sure GraalVM is enabled, e.g.:
export JAVA_HOME=/Library/Java/JavaVirtualMachines/graalvm-ce-java17-22.2.0/Contents/Home
export PATH=$JAVA_HOME/bin:$PATH      

# Run selftest with native-image-gent, build and run native-image version of selftest
cd junixsocket/junixsocket-native-graalvm
bin/build-selftest
```

Before a new release is drafted, this script needs to be run.

If there are metadata changes, they will be reported using the above script.

The new metadata files are stored under
`junixsocket/junixsocket-native-graalvm/output/META-INF/native-image/`.

Their content needs to be
analyzed and distributed among `junixsocket-common`, `junixsocket-selftest`, etc., by storing the
relevant metadata in the corresponding files under `src/main/resources/META-INF/native-image`
(subdirectories *`artifactId/groupId`*, e.g.,
`com.kohlschutter.junixsocket/junixsocket-native-graalvm`).
