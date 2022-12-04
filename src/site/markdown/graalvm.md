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
	
## Known issues

### native-image needs a lot of RAM

When you see an error message `Error: Image build request failed with exit status 137` coming from the Maven build of `junixsocket-selftest-native-image`, there was not enough RAM, and the kernel terminated the process. Either add some swap space or more RAM.

### NoSuchMethodException: ...DatagramSocketImpl.peekData

When you see errors like this, try building with a Java 19 GraalVM instead of Java 11 GraalVM:

```
Fatal error: org.graalvm.compiler.debug.GraalError: com.oracle.svm.util.ReflectionUtil$ReflectionUtilError: java.lang.NoSuchMethodException: org.newsclub.net.unix.vsock.AFVSOCKDatagramSocketImpl.peekData(java.net.DatagramPacket)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.util.AnalysisFuture.setException(AnalysisFuture.java:49)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:269)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.util.AnalysisFuture.ensureDone(AnalysisFuture.java:63)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.meta.AnalysisElement.lambda$execute$2(AnalysisElement.java:170)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.util.CompletionExecutor.executeCommand(CompletionExecutor.java:193)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.util.CompletionExecutor.lambda$executeService$0(CompletionExecutor.java:177)
	at java.base/java.util.concurrent.ForkJoinTask$RunnableExecuteAction.exec(ForkJoinTask.java:1426)
	at java.base/java.util.concurrent.ForkJoinTask.doExec(ForkJoinTask.java:290)
	at java.base/java.util.concurrent.ForkJoinPool$WorkQueue.topLevelExec(ForkJoinPool.java:1020)
	at java.base/java.util.concurrent.ForkJoinPool.scan(ForkJoinPool.java:1656)
	at java.base/java.util.concurrent.ForkJoinPool.runWorker(ForkJoinPool.java:1594)
	at java.base/java.util.concurrent.ForkJoinWorkerThread.run(ForkJoinWorkerThread.java:183)
Caused by: com.oracle.svm.util.ReflectionUtil$ReflectionUtilError: java.lang.NoSuchMethodException: org.newsclub.net.unix.vsock.AFVSOCKDatagramSocketImpl.peekData(java.net.DatagramPacket)
	at org.graalvm.nativeimage.base/com.oracle.svm.util.ReflectionUtil.lookupMethod(ReflectionUtil.java:82)
	at org.graalvm.nativeimage.base/com.oracle.svm.util.ReflectionUtil.lookupMethod(ReflectionUtil.java:69)
	at org.graalvm.nativeimage.builder/com.oracle.svm.core.jdk.JNIRegistrationUtil.method(JNIRegistrationUtil.java:91)
	at org.graalvm.nativeimage.builder/com.oracle.svm.hosted.jdk.JNIRegistrationJavaNet.lambda$registerDatagramSocketCheckOldImpl$0(JNIRegistrationJavaNet.java:230)
	at org.graalvm.nativeimage.pointsto/com.oracle.graal.pointsto.meta.AnalysisElement$SubtypeReachableNotification.lambda$notifyCallback$0(AnalysisElement.java:129)
	at java.base/java.util.concurrent.FutureTask.run(FutureTask.java:264)
	... 10 more
Caused by: java.lang.NoSuchMethodException: org.newsclub.net.unix.vsock.AFVSOCKDatagramSocketImpl.peekData(java.net.DatagramPacket)
	at java.base/java.lang.Class.getDeclaredMethod(Class.java:2475)
	at org.graalvm.nativeimage.base/com.oracle.svm.util.ReflectionUtil.lookupMethod(ReflectionUtil.java:74)
	... 15 more
```


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
