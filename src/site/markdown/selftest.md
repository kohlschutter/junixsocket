# Selftest

Since junixsocket supports many environments that can run Java and Unix domain sockets,
it is important to verify whether junixsocket works as expected on your target environment.

junixsocket-selftest provides such a self-contained test suite, in the form of a jarfile that
contains all necessary dependencies. You can run the selftest as follows:

    java -jar junixsocket-selftest-X.Y.Z-jar-with-dependencies.jar

(replace X.Y.Z with your junixsocket version)

The selftest runs a series of unit tests, also including some information from your target system
(such as system properties, OS version, etc.) and then summarizes the result of the selftest in the
form of "`PASSED`", "`FAIL`", "`PASSED WITH ISSUES`" or "`INCONCLUSIVE`".

The selftest is also able to detect common errors, such as permission errors, and add corresponding
notes in plain English as part of the selftest output.

If you encounter any selftest failures, please
[file a bug report](https://github.com/kohlschutter/junixsocket/issues) with the output of the selftest.

## Controlling what selftest does

There are several configuration options when dealing with selftest problems, usually via setting
some System properties when launching `junixsocket-selftest`.

For now, if you're interested in these properties, please consult the Selftest source code.
In general, it is not expected that you have to set any of those.

## Example output

Running the selftest should output something like this below. Please note that the output format
is not guaranteed to be stable, however it can be assumed that a line with `Selftest PASSED`
indicates success:

This program determines whether junixsocket is supported on the current platform.
The final line should say whether the selftest passed or failed.

If the selftest failed, please visit https://github.com/kohlschutter/junixsocket/issues
and file a new bug report with the output below.

    junixsocket selftest version X.Y.Z

    Git properties:

    git.build.version: X.Y.Z
    git.commit.id.abbrev: 6ef19b4
    git.commit.id.describe: junixsocket-X.Y.Z
    git.commit.id.full: 6ef19b41225c5369f1c104d45d8d85efa9b057b5
    git.commit.time: 2022-10-25T22:09:40+02:00
    git.dirty: false

    System properties:

    file.encoding: UTF-8
    file.separator: /
    java.class.path: junixsocket-selftest-X.Y.Z-jar-with-dependencies.jar
    java.class.version: 61.0
    java.home: /usr/lib/jvm/java-17-openjdk
    java.io.tmpdir: /tmp
    java.library.path: /usr/lib/jvm/java-17-openjdk/lib/server:/usr/lib/jvm/java-17-openjdk/lib:/usr/lib/jvm/java-17-openjdk/../lib:/usr/java/packages/lib:/usr/lib64:/lib64:/lib:/usr/lib
    java.runtime.name: OpenJDK Runtime Environment
    java.runtime.version: 17.0.4.1+1-alpine-r0
    java.specification.name: Java Platform API Specification
    java.specification.vendor: Oracle Corporation
    java.specification.version: 17
    java.vendor: Alpine
    java.vendor.url: https://alpinelinux.org/
    java.vendor.url.bug: https://gitlab.alpinelinux.org/alpine/aports/issues
    java.version: 17.0.4.1
    java.version.date: 2022-08-12
    java.vm.compressedOopsMode: 32-bit
    java.vm.info: mixed mode, sharing
    java.vm.name: OpenJDK 64-Bit Server VM
    java.vm.specification.name: Java Virtual Machine Specification
    java.vm.specification.vendor: Oracle Corporation
    java.vm.specification.version: 17
    java.vm.vendor: Alpine
    java.vm.version: 17.0.4.1+1-alpine-r0
    jdk.debug: release
    line.separator: \n
    native.encoding: UTF-8
    os.arch: aarch64
    os.name: Linux
    os.version: 6.0.0-rc7
    path.separator: :
    sun.arch.data.model: 64
    sun.boot.library.path: /usr/lib/jvm/java-17-openjdk/lib
    sun.cpu.endian: little
    sun.io.unicode.encoding: UnicodeLittle
    sun.java.command: junixsocket-selftest-X.Y.Z-jar-with-dependencies.jar
    sun.java.launcher: SUN_STANDARD
    sun.jnu.encoding: UTF-8
    sun.management.compiler: HotSpot 64-Bit Tiered Compilers
    sun.stderr.encoding: UTF-8
    user.country: US
    user.dir: /home/ck
    user.home: /home/ck
    user.language: en
    user.name: ck

    BEGIN contents of file: /etc/os-release
    NAME="Alpine Linux"
    ID=alpine
    VERSION_ID=3.16.2
    PRETTY_NAME="Alpine Linux v3.16"
    HOME_URL="https://alpinelinux.org/"
    BUG_REPORT_URL="https://gitlab.alpinelinux.org/alpine/aports/-/issues"
    =END= contents of file: /etc/os-release

    AFSocket.isSupported: true

    AFUNIXSocket.isSupported: true

    Testing "junixsocket-common"... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testParseFail()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testSchemesAvailable()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testGeneric()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testSocatString()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testSocketURI()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testServiceRangeURI()... 
    Testing "junixsocket-common"... AFTIPCSocketAddressTest.testServiceURI()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testSchemesAvailable()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testSocatString()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testURITemplateWithPortNumber()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testURITemplate()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testHttpUnix()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testUnixScheme()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testFileScheme()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testAbstractNamespace()... 
    Testing "junixsocket-common"... AFUNIXSocketAddressTest.testParseURIandBack()... 
    Testing "junixsocket-common"... AbstractNamespaceTest.testBindTrailingZeroes()... 
    Testing "junixsocket-common"... AbstractNamespaceTest.testBind()... 
    Testing "junixsocket-common"... AbstractNamespaceTest.testBindLongAbstractAddress()... 
    Testing "junixsocket-common"... AcceptTimeoutTest.testCatchTimeout()... 
    Testing "junixsocket-common"... AcceptTimeoutTest.testTimeoutAfterDelay()... 
    Testing "junixsocket-common"... AcceptTimeoutTest.testAcceptWithoutBindToService()... 
    Testing "junixsocket-common"... AvailableTest.testAvailableAtClient()... 
    Testing "junixsocket-common"... AvailableTest.testAvailableAtServer()... 
    Testing "junixsocket-common"... BufferOverflowTest.writeOverflow()... 
    Testing "junixsocket-common"... BufferOverflowTest.readUpTo()... 
    Testing "junixsocket-common"... BufferOverflowTest.readOutOfBounds()... 
    Testing "junixsocket-common"... CancelAcceptTest.issue6test1()... 
    Testing "junixsocket-common"... DatagramSocketTest.testBindConnect()... 
    Testing "junixsocket-common"... DatagramSocketTest.testPeekTimeout()... 
    Testing "junixsocket-common"... DatagramSocketTest.testReadTimeout()... 
    Testing "junixsocket-common"... EndOfFileTest.clientWriteToSocketClosedByClient()... 
    Testing "junixsocket-common"... EndOfFileTest.clientWriteToSocketClosedByServer()... 
    Testing "junixsocket-common"... EndOfFileTest.bidirectionalSanity()... 
    Testing "junixsocket-common"... EndOfFileTest.serverWriteToSocketClosedByClient()... 
    Testing "junixsocket-common"... EndOfFileTest.serverWriteToSocketClosedByServer()... 
    Testing "junixsocket-common"... EndOfFileTest.clientReadEof()... 
    Testing "junixsocket-common"... EndOfFileTest.serverReadEof()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testInvalidFileDescriptor()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testPipe()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testAvailableTypes()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testStdout()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testRandomAccessFile()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testUnconnectedServerAsSocket()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testSocketPairNative()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testDatagramFileChannel()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testSocketPair()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testDatagramSocket()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testSocketPorts()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testDatagramPorts()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testForkedVMRedirectStdin()... 
    Testing "junixsocket-common"... FileDescriptorCastTest.testServer()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testNullFileDescriptorArray()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testNoAncillaryReceiveBuffer()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testSendRecvFileDescriptors()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testBadFileDescriptor()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testDatagramSocket()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testAncillaryReceiveBufferTooSmall()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testFileInputStreamPartiallyConsumed()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testEmptyFileDescriptorArray()... 
    Testing "junixsocket-common"... FileDescriptorsTest.testFileInputStream()... 
    Testing "junixsocket-common"... InetAddressTest.testFromToBytes()... 
    Testing "junixsocket-common"... InetAddressTest.testHostnameString()... 
    Testing "junixsocket-common"... InetAddressTest.testIsLoopbackAddress()... 
    Testing "junixsocket-common"... InetAddressTest.testHostnameStringEndsWithJunixSocket()... 
    Testing "junixsocket-common"... PeerCredentialsTest.testSocketsSameProcess()... 
    Testing "junixsocket-common"... PeerCredentialsTest.testDatagramSocket()... 
    Supported credentials:   pid uid gid additional_gids
    Unsupported credentials: uuid
    Testing "junixsocket-common"... PipeTest.testPipe()... 
    Testing "junixsocket-common"... PipeTest.testPipeRecvHang()... 
    Testing "junixsocket-common"... ReadWriteTest.testReceiveWithByteArraySendByteForByte()... 
    Testing "junixsocket-common"... ReadWriteTest.testReceiveWithByteArraySendWithByteArray()... 
    Testing "junixsocket-common"... ReadWriteTest.testReceiveDataByteForByteSendByteForByte()... 
    Testing "junixsocket-common"... ReadWriteTest.testReceiveDataByteForByteSendWithByteArray()... 
    Testing "junixsocket-common"... SelectorTest.testNonBlockingAccept()... 
    Testing "junixsocket-common"... SelectorTest.testClosedSelectorSelect()... 
    Testing "junixsocket-common"... SelectorTest.testClosedSelectorWakeup()... 
    Testing "junixsocket-common"... SelectorTest.testCancelSelect()... 
    Testing "junixsocket-common"... SelectorTest.testConnectionCloseImmediateClientDisconnect()... 
    Testing "junixsocket-common"... SelectorTest.testConnectionCloseImmediateClientDisconnectKeepLooping()... 
    Testing "junixsocket-common"... SelectorTest.testConnectionCloseEventualClientDisconnectKeepLooping()... 
    Testing "junixsocket-common"... SelectorTest.testConnectionCloseEventualClientDisconnect()... 
    Testing "junixsocket-common"... ServerSocketCloseTest.testUnblockAcceptsWithSoTimeout()... 
    Testing "junixsocket-common"... ServerSocketCloseTest.testUnblockAcceptsWithoutSoTimeout()... 
    Testing "junixsocket-common"... ServerSocketTest.testUnboundServerSocket()... 
    Testing "junixsocket-common"... ServerSocketTest.testBindBadArguments()... 
    Testing "junixsocket-common"... ServerSocketTest.testCloseable()... 
    Testing "junixsocket-common"... ServerSocketTest.testSupported()... 
    Testing "junixsocket-common"... SocketAddressTest.testInetAddress()... 
    Testing "junixsocket-common"... SocketAddressTest.testPath()... 
    Testing "junixsocket-common"... SocketAddressTest.testPort()... 
    Testing "junixsocket-common"... SocketAddressTest.testEmptyAddress()... 
    Testing "junixsocket-common"... SocketAddressTest.testLegacyConstructor()... 
    Testing "junixsocket-common"... SocketAddressTest.testByteConstructor()... 
    Testing "junixsocket-common"... SocketAddressTest.testAbstractNamespace()... 
    Testing "junixsocket-common"... SocketChannelTest.testDoubleBindAddressReusable()... 
    Testing "junixsocket-common"... SocketChannelTest.testDoubleBindAddressNotReusable()... 
    Testing "junixsocket-common"... SocketChannelTest.testNonBlockingConnect()... 
    Testing "junixsocket-common"... SocketFactoryTest.testURISchemeCeateSocketWithInvalidHostname()... 
    Testing "junixsocket-common"... SocketFactoryTest.testURISchemeCeateSocketWithIllegalArguments()... 
    Testing "junixsocket-common"... SocketFactoryTest.testURISchemeCeateSocketThenConnect()... 
    Testing "junixsocket-common"... SocketFactoryTest.testURISchemeCeateSocketWithHostnameValidCases()... 
    Testing "junixsocket-common"... SocketFactoryTest.testSystemProperty()... 
    Testing "junixsocket-common"... SocketFactoryTest.testFactoryArg()... 
    Testing "junixsocket-common"... SocketPairTest.testDatagramPair()... 
    Testing "junixsocket-common"... SocketPairTest.testSocketPair()... 
    Testing "junixsocket-common"... SocketTest.testConnectBadArguments()... 
    Testing "junixsocket-common"... SocketTest.testBindBadArguments()... 
    Testing "junixsocket-common"... SocketTest.testCloseable()... 
    Testing "junixsocket-common"... SocketTest.testUnconnectedSocket()... 
    Testing "junixsocket-common"... SocketTest.testMain()... 
    org.newsclub.net.unix.AFUNIXSocket.isSupported(): true
    CAPABILITY_PEER_CREDENTIALS: true
    CAPABILITY_ANCILLARY_MESSAGES: true
    CAPABILITY_FILE_DESCRIPTORS: true
    CAPABILITY_ABSTRACT_NAMESPACE: true
    CAPABILITY_UNIX_DATAGRAMS: true
    CAPABILITY_NATIVE_SOCKETPAIR: true
    CAPABILITY_FD_AS_REDIRECT: true
    CAPABILITY_TIPC: false
    CAPABILITY_UNIX_DOMAIN: true
    CAPABILITY_VSOCK: true
    CAPABILITY_VSOCK_DGRAM: false
    CAPABILITY_ZERO_LENGTH_SEND: true
    Testing "junixsocket-common"... SocketTest.testLoadedLibrary()... 
    Testing "junixsocket-common"... SocketTest.testSupports()... 
    Testing "junixsocket-common"... SocketTest.testVersion()... 
    Testing "junixsocket-common"... SocketTest.testReceivedFileDescriptorsUnconnected()... 
    Testing "junixsocket-common"... SocketTest.testSupported()... 
    Testing "junixsocket-common"... SoTimeoutTest.issue14Fail()... 
    Testing "junixsocket-common"... SoTimeoutTest.issue14Pass()... 
    Testing "junixsocket-common"... SoTimeoutTest.testSocketTimeoutExceptionRead()... 
    Testing "junixsocket-common"... SoTimeoutTest.testSocketTimeoutExceptionWrite()... 
    Testing "junixsocket-common"... StandardSocketOptionsTest.testUnconnectedServerSocketOptions()... 
    Testing "junixsocket-common"... StandardSocketOptionsTest.testSocketOptions()... 
    Testing "junixsocket-common"... TcpNoDelayTest.testDefaultImpl()... 
    Testing "junixsocket-common"... TcpNoDelayTest.testStrictImpl()... 
    Testing "junixsocket-common"... ThroughputTest.testDatagramChannel()... 
    Testing "junixsocket-common"... ThroughputTest.testDatagramChannelDirect()... 
    Testing "junixsocket-common"... ThroughputTest.testDatagramChannelNonBlocking()... 
    Testing "junixsocket-common"... ThroughputTest.testDatagramChannelNonBlockingDirect()... 
    Testing "junixsocket-common"... ThroughputTest.testDatagramPacket()... 
    Testing "junixsocket-common"... ThroughputTest.testSocket()... 
    Testing "junixsocket-common"... ThroughputTest.testSocketChannel()... 
    Testing "junixsocket-common"... ThroughputTest.testSocketChannelDirectBuffer()... 
    Testing "junixsocket-common"... done
    .
    '-- JUnit Jupiter [OK]
      +-- AFTIPCSocketAddressTest [OK]
      | +-- testParseFail() [OK]
      | +-- testSchemesAvailable() [OK]
      | +-- testGeneric() [OK]
      | +-- testSocatString() [OK]
      | +-- testSocketURI() [OK]
      | +-- testServiceRangeURI() [OK]
      | '-- testServiceURI() [OK]
      +-- AFUNIXSocketAddressTest [OK]
      | +-- testSchemesAvailable() [OK]
      | +-- testSocatString() [OK]
      | +-- testURITemplateWithPortNumber() [OK]
      | +-- testURITemplate() [OK]
      | +-- testHttpUnix() [OK]
      | +-- testUnixScheme() [OK]
      | +-- testFileScheme() [OK]
      | +-- testAbstractNamespace() [OK]
      | '-- testParseURIandBack() [OK]
      +-- AbstractNamespaceTest [OK]
      | +-- testBindTrailingZeroes() [OK]
      | +-- testBind() [OK]
      | '-- testBindLongAbstractAddress() [OK]
      +-- AcceptTimeoutTest [OK]
      | +-- testCatchTimeout() [OK]
      | +-- testTimeoutAfterDelay() [OK]
      | '-- testAcceptWithoutBindToService() [OK]
      +-- AvailableTest [OK]
      | +-- testAvailableAtClient() [OK]
      | '-- testAvailableAtServer() [OK]
      +-- BufferOverflowTest [OK]
      | +-- writeOverflow() [OK]
      | +-- readUpTo() [OK]
      | '-- readOutOfBounds() [OK]
      +-- CancelAcceptTest [OK]
      | '-- issue6test1() [OK]
      +-- DatagramSocketTest [OK]
      | +-- testBindConnect() [OK]
      | +-- testPeekTimeout() [OK]
      | '-- testReadTimeout() [OK]
      +-- EndOfFileTest [OK]
      | +-- clientWriteToSocketClosedByClient() [OK]
      | +-- clientWriteToSocketClosedByServer() [OK]
      | +-- bidirectionalSanity() [OK]
      | +-- serverWriteToSocketClosedByClient() [OK]
      | +-- serverWriteToSocketClosedByServer() [OK]
      | +-- clientReadEof() [OK]
      | '-- serverReadEof() [OK]
      +-- FileDescriptorCastTest [OK]
      | +-- testInvalidFileDescriptor() [OK]
      | +-- testPipe() [OK]
      | +-- testAvailableTypes() [OK]
      | +-- testStdout() [OK]
      | '-- testRandomAccessFile() [OK]
      +-- FileDescriptorCastTest [OK]
      | +-- testUnconnectedServerAsSocket() [OK]
      | +-- testSocketPairNative() [OK]
      | +-- testDatagramFileChannel() [OK]
      | +-- testSocketPair() [OK]
      | +-- testDatagramSocket() [OK]
      | +-- testSocketPorts() [OK]
      | +-- testDatagramPorts() [OK]
      | +-- testForkedVMRedirectStdin() [OK]
      | '-- testServer() [OK]
      +-- FileDescriptorsTest [OK]
      | +-- testNullFileDescriptorArray() [OK]
      | +-- testNoAncillaryReceiveBuffer() [OK]
      | +-- testSendRecvFileDescriptors() [OK]
      | +-- testBadFileDescriptor() [OK]
      | +-- testDatagramSocket() [OK]
      | +-- testAncillaryReceiveBufferTooSmall() [OK]
      | +-- testFileInputStreamPartiallyConsumed() [OK]
      | +-- testEmptyFileDescriptorArray() [OK]
      | '-- testFileInputStream() [OK]
      +-- InetAddressTest [OK]
      | +-- testFromToBytes() [OK]
      | +-- testHostnameString() [OK]
      | +-- testIsLoopbackAddress() [OK]
      | '-- testHostnameStringEndsWithJunixSocket() [OK]
      +-- PeerCredentialsTest [OK]
      | +-- testSocketsSameProcess() [OK]
      | '-- testDatagramSocket() [OK]
      +-- PipeTest [OK]
      | +-- testPipe() [OK]
      | '-- testPipeRecvHang() [OK]
      +-- ReadWriteTest [OK]
      | +-- testReceiveWithByteArraySendByteForByte() [OK]
      | +-- testReceiveWithByteArraySendWithByteArray() [OK]
      | +-- testReceiveDataByteForByteSendByteForByte() [OK]
      | '-- testReceiveDataByteForByteSendWithByteArray() [OK]
      +-- SelectorTest [OK]
      | +-- testNonBlockingAccept() [OK]
      | +-- testClosedSelectorSelect() [OK]
      | +-- testClosedSelectorWakeup() [OK]
      | +-- testCancelSelect() [OK]
      | +-- testConnectionCloseImmediateClientDisconnect() [OK]
      | +-- testConnectionCloseImmediateClientDisconnectKeepLooping() [OK]
      | +-- testConnectionCloseEventualClientDisconnectKeepLooping() [OK]
      | '-- testConnectionCloseEventualClientDisconnect() [OK]
      +-- ServerSocketCloseTest [OK]
      | +-- testUnblockAcceptsWithSoTimeout() [OK]
      | '-- testUnblockAcceptsWithoutSoTimeout() [OK]
      +-- ServerSocketTest [OK]
      | +-- testUnboundServerSocket() [OK]
      | +-- testBindBadArguments() [OK]
      | +-- testCloseable() [OK]
      | '-- testSupported() [OK]
      +-- SocketAddressTest [OK]
      | +-- testInetAddress() [OK]
      | +-- testPath() [OK]
      | +-- testPort() [OK]
      | +-- testEmptyAddress() [OK]
      | +-- testLegacyConstructor() [OK]
      | +-- testByteConstructor() [OK]
      | '-- testAbstractNamespace() [OK]
      +-- SocketChannelTest [OK]
      | +-- testDoubleBindAddressReusable() [OK]
      | +-- testDoubleBindAddressNotReusable() [OK]
      | '-- testNonBlockingConnect() [OK]
      +-- SocketFactoryTest [OK]
      | +-- testURISchemeCeateSocketWithInvalidHostname() [OK]
      | +-- testURISchemeCeateSocketWithIllegalArguments() [OK]
      | +-- testURISchemeCeateSocketThenConnect() [OK]
      | +-- testURISchemeCeateSocketWithHostnameValidCases() [OK]
      | +-- testSystemProperty() [OK]
      | '-- testFactoryArg() [OK]
      +-- SocketPairTest [OK]
      | +-- testDatagramPair() [OK]
      | '-- testSocketPair() [OK]
      +-- SocketTest [OK]
      | +-- testConnectBadArguments() [OK]
      | +-- testBindBadArguments() [OK]
      | +-- testCloseable() [OK]
      | +-- testUnconnectedSocket() [OK]
      | +-- testMain() [OK]
      | +-- testLoadedLibrary() [OK]
      | +-- testSupports() [OK]
      | +-- testVersion() [OK]
      | +-- testReceivedFileDescriptorsUnconnected() [OK]
      | '-- testSupported() [OK]
      +-- SoTimeoutTest [OK]
      | +-- issue14Fail() [OK]
      | +-- issue14Pass() [OK]
      | +-- testSocketTimeoutExceptionRead() [OK]
      | '-- testSocketTimeoutExceptionWrite() [OK]
      +-- StandardSocketOptionsTest [OK]
      | +-- testUnconnectedServerSocketOptions() [OK]
      | '-- testSocketOptions() [OK]
      +-- TcpNoDelayTest [OK]
      | +-- testDefaultImpl() [OK]
      | '-- testStrictImpl() [OK]
      '-- ThroughputTest [OK]
        +-- testDatagramChannel() [OK]
        +-- testDatagramChannelDirect() [OK]
        +-- testDatagramChannelNonBlocking() [OK]
        +-- testDatagramChannelNonBlockingDirect() [OK]
        +-- testDatagramPacket() [OK]
        +-- testSocket() [OK]
        +-- testSocketChannel() [OK]
        '-- testSocketChannelDirectBuffer() [OK]

    Test run finished after 9482 ms
    [        29 containers found      ]
    [         0 containers skipped    ]
    [        29 containers started    ]
    [         0 containers aborted    ]
    [        29 containers successful ]
    [         0 containers failed     ]
    [       131 tests found           ]
    [         0 tests skipped         ]
    [       131 tests started         ]
    [         0 tests aborted         ]
    [       131 tests successful      ]
    [         0 tests failed          ]

    Testing "junixsocket-tipc"... 
    Testing "junixsocket-tipc"... done
    .
    '-- JUnit Jupiter [OK]
      +-- AcceptTimeoutTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- AFTIPCTopologyWatcherTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- AncillaryMessageTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- AvailableTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- BufferOverflowTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- CancelAcceptTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- DatagramSocketTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- EndOfFileTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- ReadWriteTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SelectorTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- ServerSocketCloseTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- ServerSocketTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SocketChannelTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SocketOptionsTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SocketPairTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SocketTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- SoTimeoutTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- StandardSocketOptionsTest [S] Missing capabilities: [CAPABILITY_TIPC]
      +-- TcpNoDelayTest [S] Missing capabilities: [CAPABILITY_TIPC]
      '-- ThroughputTest [S] Missing capabilities: [CAPABILITY_TIPC]

    Test run finished after 100 ms
    [        21 containers found      ]
    [        20 containers skipped    ]
    [         1 containers started    ]
    [         0 containers aborted    ]
    [         1 containers successful ]
    [         0 containers failed     ]
    [        80 tests found           ]
    [        80 tests skipped         ]
    [         0 tests started         ]
    [         0 tests aborted         ]
    [         0 tests successful      ]
    [         0 tests failed          ]

    Testing "junixsocket-vsock"... 
    Testing "junixsocket-vsock"... AFVSOCKExtensionsTest.testGetLocalID()... 
    Local CID: 2
    Testing "junixsocket-vsock"... AcceptTimeoutTest.testCatchTimeout()... 
    Testing "junixsocket-vsock"... AcceptTimeoutTest.testAcceptWithoutBindToService()... 
    Testing "junixsocket-vsock"... AcceptTimeoutTest.testTimeoutAfterDelay()... 
    Testing "junixsocket-vsock"... AvailableTest.testAvailableAtClient()... 
    Testing "junixsocket-vsock"... AvailableTest.testAvailableAtServer()... 
    Testing "junixsocket-vsock"... BufferOverflowTest.writeOverflow()... 
    Testing "junixsocket-vsock"... BufferOverflowTest.readUpTo()... 
    Testing "junixsocket-vsock"... BufferOverflowTest.readOutOfBounds()... 
    Testing "junixsocket-vsock"... CancelAcceptTest.issue6test1()... 
    Testing "junixsocket-vsock"... EndOfFileTest.clientWriteToSocketClosedByClient()... 
    Testing "junixsocket-vsock"... EndOfFileTest.clientWriteToSocketClosedByServer()... 
    Testing "junixsocket-vsock"... EndOfFileTest.bidirectionalSanity()... 
    Testing "junixsocket-vsock"... EndOfFileTest.serverWriteToSocketClosedByClient()... 
    Testing "junixsocket-vsock"... EndOfFileTest.serverWriteToSocketClosedByServer()... 
    Testing "junixsocket-vsock"... EndOfFileTest.clientReadEof()... 
    Testing "junixsocket-vsock"... EndOfFileTest.serverReadEof()... 
    Testing "junixsocket-vsock"... ReadWriteTest.testReceiveWithByteArraySendByteForByte()... 
    Testing "junixsocket-vsock"... ReadWriteTest.testReceiveWithByteArraySendWithByteArray()... 
    Testing "junixsocket-vsock"... ReadWriteTest.testReceiveDataByteForByteSendByteForByte()... 
    Testing "junixsocket-vsock"... ReadWriteTest.testReceiveDataByteForByteSendWithByteArray()... 
    Testing "junixsocket-vsock"... SelectorTest.testClosedSelectorSelect()... 
    Testing "junixsocket-vsock"... SelectorTest.testClosedSelectorWakeup()... 
    Testing "junixsocket-vsock"... SelectorTest.testCancelSelect()... 
    Testing "junixsocket-vsock"... SelectorTest.testConnectionCloseImmediateClientDisconnect()... 
    Testing "junixsocket-vsock"... SelectorTest.testConnectionCloseImmediateClientDisconnectKeepLooping()... 
    Testing "junixsocket-vsock"... SelectorTest.testConnectionCloseEventualClientDisconnectKeepLooping()... 
    Testing "junixsocket-vsock"... SelectorTest.testConnectionCloseEventualClientDisconnect()... 
    Testing "junixsocket-vsock"... SelectorTest.testNonBlockingAccept()... 
    Testing "junixsocket-vsock"... ServerSocketCloseTest.testUnblockAcceptsWithSoTimeout()... 
    Testing "junixsocket-vsock"... ServerSocketCloseTest.testUnblockAcceptsWithoutSoTimeout()... 
    Testing "junixsocket-vsock"... ServerSocketTest.testUnboundServerSocket()... 
    Testing "junixsocket-vsock"... ServerSocketTest.testBindBadArguments()... 
    Testing "junixsocket-vsock"... ServerSocketTest.testCloseable()... 
    Testing "junixsocket-vsock"... ServerSocketTest.testSupported()... 
    Testing "junixsocket-vsock"... SocketChannelTest.testDoubleBindAddressReusable()... 
    Testing "junixsocket-vsock"... SocketChannelTest.testDoubleBindAddressNotReusable()... 
    Testing "junixsocket-vsock"... SocketChannelTest.testNonBlockingConnect()... 
    Testing "junixsocket-vsock"... SocketPairTest.testDatagramPair()... 
    Testing "junixsocket-vsock"... SocketPairTest.testSocketPair()... 
    Testing "junixsocket-vsock"... SocketTest.testConnectBadArguments()... 
    Testing "junixsocket-vsock"... SocketTest.testBindBadArguments()... 
    Testing "junixsocket-vsock"... SocketTest.testCloseable()... 
    Testing "junixsocket-vsock"... SocketTest.testUnconnectedSocket()... 
    Testing "junixsocket-vsock"... SocketTest.testMain()... 
    org.newsclub.net.unix.vsock.AFVSOCKSocket.isSupported(): true
    Testing "junixsocket-vsock"... SocketTest.testLoadedLibrary()... 
    Testing "junixsocket-vsock"... SocketTest.testVersion()... 
    Testing "junixsocket-vsock"... SocketTest.testSupported()... 
    Testing "junixsocket-vsock"... SoTimeoutTest.issue14Fail()... 
    Testing "junixsocket-vsock"... SoTimeoutTest.issue14Pass()... 
    Testing "junixsocket-vsock"... SoTimeoutTest.testSocketTimeoutExceptionRead()... 
    Testing "junixsocket-vsock"... SoTimeoutTest.testSocketTimeoutExceptionWrite()... 
    Testing "junixsocket-vsock"... StandardSocketOptionsTest.testUnconnectedServerSocketOptions()... 
    Testing "junixsocket-vsock"... StandardSocketOptionsTest.testSocketOptions()... 
    Testing "junixsocket-vsock"... TcpNoDelayTest.testDefaultImpl()... 
    Testing "junixsocket-vsock"... TcpNoDelayTest.testStrictImpl()... 
    Testing "junixsocket-vsock"... ThroughputTest.testSocket()... 
    Testing "junixsocket-vsock"... ThroughputTest.testSocketChannel()... 
    Testing "junixsocket-vsock"... ThroughputTest.testSocketChannelDirectBuffer()... 
    Testing "junixsocket-vsock"... done
    .
    '-- JUnit Jupiter [OK]
      +-- AFVSOCKExtensionsTest [OK]
      | '-- testGetLocalID() [OK]
      +-- AcceptTimeoutTest [OK]
      | +-- testCatchTimeout() [OK]
      | +-- testAcceptWithoutBindToService() [OK]
      | '-- testTimeoutAfterDelay() [OK]
      +-- AvailableTest [OK]
      | +-- testAvailableAtClient() [OK]
      | '-- testAvailableAtServer() [OK]
      +-- BufferOverflowTest [OK]
      | +-- writeOverflow() [OK]
      | +-- readUpTo() [OK]
      | '-- readOutOfBounds() [OK]
      +-- CancelAcceptTest [OK]
      | '-- issue6test1() [OK]
      +-- DatagramSocketTest [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
      +-- EndOfFileTest [OK]
      | +-- clientWriteToSocketClosedByClient() [OK]
      | +-- clientWriteToSocketClosedByServer() [OK]
      | +-- bidirectionalSanity() [OK]
      | +-- serverWriteToSocketClosedByClient() [OK]
      | +-- serverWriteToSocketClosedByServer() [OK]
      | +-- clientReadEof() [OK]
      | '-- serverReadEof() [OK]
      +-- ReadWriteTest [OK]
      | +-- testReceiveWithByteArraySendByteForByte() [OK]
      | +-- testReceiveWithByteArraySendWithByteArray() [OK]
      | +-- testReceiveDataByteForByteSendByteForByte() [OK]
      | '-- testReceiveDataByteForByteSendWithByteArray() [OK]
      +-- SelectorTest [OK]
      | +-- testClosedSelectorSelect() [OK]
      | +-- testClosedSelectorWakeup() [OK]
      | +-- testCancelSelect() [OK]
      | +-- testConnectionCloseImmediateClientDisconnect() [OK]
      | +-- testConnectionCloseImmediateClientDisconnectKeepLooping() [OK]
      | +-- testConnectionCloseEventualClientDisconnectKeepLooping() [OK]
      | +-- testConnectionCloseEventualClientDisconnect() [OK]
      | '-- testNonBlockingAccept() [OK]
      +-- ServerSocketCloseTest [OK]
      | +-- testUnblockAcceptsWithSoTimeout() [OK]
      | '-- testUnblockAcceptsWithoutSoTimeout() [OK]
      +-- ServerSocketTest [OK]
      | +-- testUnboundServerSocket() [OK]
      | +-- testBindBadArguments() [OK]
      | +-- testCloseable() [OK]
      | '-- testSupported() [OK]
      +-- SocketChannelTest [OK]
      | +-- testDoubleBindAddressReusable() [OK]
      | +-- testDoubleBindAddressNotReusable() [OK]
      | '-- testNonBlockingConnect() [OK]
      +-- SocketPairTest [OK]
      | +-- testDatagramPair() [OK]
      | '-- testSocketPair() [OK]
      +-- SocketTest [OK]
      | +-- testConnectBadArguments() [OK]
      | +-- testBindBadArguments() [OK]
      | +-- testCloseable() [OK]
      | +-- testUnconnectedSocket() [OK]
      | +-- testMain() [OK]
      | +-- testLoadedLibrary() [OK]
      | +-- testVersion() [OK]
      | '-- testSupported() [OK]
      +-- SoTimeoutTest [OK]
      | +-- issue14Fail() [OK]
      | +-- issue14Pass() [OK]
      | +-- testSocketTimeoutExceptionRead() [OK]
      | '-- testSocketTimeoutExceptionWrite() [OK]
      +-- StandardSocketOptionsTest [OK]
      | +-- testUnconnectedServerSocketOptions() [OK]
      | '-- testSocketOptions() [OK]
      +-- TcpNoDelayTest [OK]
      | +-- testDefaultImpl() [OK]
      | '-- testStrictImpl() [OK]
      '-- ThroughputTest [OK]
        +-- testDatagramChannel() [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
        +-- testDatagramChannelDirect() [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
        +-- testDatagramChannelNonBlocking() [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
        +-- testDatagramChannelNonBlockingDirect() [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
        +-- testDatagramPacket() [S] Missing capabilities: [CAPABILITY_VSOCK_DGRAM]
        +-- testSocket() [OK]
        +-- testSocketChannel() [OK]
        '-- testSocketChannelDirectBuffer() [OK]

    Test run finished after 5210 ms
    [        19 containers found      ]
    [         1 containers skipped    ]
    [        18 containers started    ]
    [         0 containers aborted    ]
    [        18 containers successful ]
    [         0 containers failed     ]
    [        67 tests found           ]
    [         8 tests skipped         ]
    [        59 tests started         ]
    [         0 tests aborted         ]
    [        59 tests successful      ]
    [         0 tests failed          ]

    Testing "junixsocket-rmi"... 
    Testing "junixsocket-rmi"... RegistryTest.testDoubleCreateRegistry()... 
    Testing "junixsocket-rmi"... RegistryTest.testExportAndBind()... 
    Testing "junixsocket-rmi"... RemoteCloseableTest.testRemoteCloseableWithANotCloseableThing()... 
    Testing "junixsocket-rmi"... RemoteCloseableTest.testRemoteCloseableWithACloseableThing()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testReadWrite()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testFindSocketFactory()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testRemoteStdoutNoop()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testRemoteStdout()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testWriteAndReadHello()... 
    Testing "junixsocket-rmi"... RemoteFileDescriptorTest.testServiceProxy()... 
    Testing "junixsocket-rmi"... RMIPeerCredentialsTest.testRemotePeerCredentials()... 
    Testing "junixsocket-rmi"... JunixsocketVersionTest.testVersion()... 
    Testing "junixsocket-rmi"... done
    .
    '-- JUnit Jupiter [OK]
      +-- RegistryTest [OK]
      | +-- testDoubleCreateRegistry() [OK]
      | '-- testExportAndBind() [OK]
      +-- RemoteCloseableTest [OK]
      | +-- testRemoteCloseableWithANotCloseableThing() [OK]
      | '-- testRemoteCloseableWithACloseableThing() [OK]
      +-- RemoteFileDescriptorTest [OK]
      | +-- testReadWrite() [OK]
      | +-- testFindSocketFactory() [OK]
      | +-- testRemoteStdoutNoop() [OK]
      | +-- testRemoteStdout() [OK]
      | +-- testWriteAndReadHello() [OK]
      | '-- testServiceProxy() [OK]
      +-- RMIPeerCredentialsTest [OK]
      | '-- testRemotePeerCredentials() [OK]
      '-- JunixsocketVersionTest [OK]
        '-- testVersion() [OK]

    Test run finished after 864 ms
    [         6 containers found      ]
    [         0 containers skipped    ]
    [         6 containers started    ]
    [         0 containers aborted    ]
    [         6 containers successful ]
    [         0 containers failed     ]
    [        12 tests found           ]
    [         0 tests skipped         ]
    [        12 tests started         ]
    [         0 tests aborted         ]
    [        12 tests successful      ]
    [         0 tests failed          ]

    Skipping optional module: junixsocket-common.JavaInet; enable by launching with -Dselftest.enable-module.junixsocket-common.JavaInet=true

    Selftest results:
    PASS	junixsocket-common	131/131
    PASS	junixsocket-tipc	0/80 (80 skipped)
    PASS	junixsocket-vsock	59/67 (8 skipped)
    PASS	junixsocket-rmi	12/12

    Supported capabilities:   [CAPABILITY_PEER_CREDENTIALS, CAPABILITY_ANCILLARY_MESSAGES, CAPABILITY_FILE_DESCRIPTORS, CAPABILITY_ABSTRACT_NAMESPACE, CAPABILITY_UNIX_DATAGRAMS, CAPABILITY_NATIVE_SOCKETPAIR, CAPABILITY_FD_AS_REDIRECT, CAPABILITY_UNIX_DOMAIN, CAPABILITY_VSOCK, CAPABILITY_ZERO_LENGTH_SEND]
    Unsupported capabilities: [CAPABILITY_TIPC, CAPABILITY_VSOCK_DGRAM]

    Selftest PASSED
