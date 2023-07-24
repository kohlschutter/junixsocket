/*
 * junixsocket
 *
 * Copyright 2009-2021 Christian Kohlschütter
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "config.h"
#include "capabilities.h"

#include "filedescriptors.h"
#include "init.h"
#include "reflection.h"

CK_IGNORE_UNUSED_VARIABLE_BEGIN
// see AFSocketCapability.java in junixsocket-common
static jint CAPABILITY_PEER_CREDENTIALS = (1 << 0);
static jint CAPABILITY_ANCILLARY_MESSAGES = (1 << 1);
static jint CAPABILITY_FILE_DESCRIPTORS = (1 << 2);
static jint CAPABILITY_ABSTRACT_NAMESPACE = (1 << 3);
static jint CAPABILITY_UNIX_DATAGRAMS = (1 << 4);
static jint CAPABILITY_NATIVE_SOCKETPAIR = (1 << 5);
static jint CAPABILITY_FD_AS_REDIRECT = (1 << 6);
static jint CAPABILITY_TIPC = (1 << 7);
static jint CAPABILITY_UNIX_DOMAIN = (1 << 8);
static jint CAPABILITY_VSOCK = (1 << 9);
static jint CAPABILITY_VSOCK_DGRAM = (1  << 10);
static jint CAPABILITY_ZERO_LENGTH_SEND = (1 << 11);
static jint CAPABILITY_UNSAFE = (1 << 12);
static jint CAPABILITY_LARGE_PORTS = (1 << 13);
static jint CAPABILITY_DARWIN = (1 << 14);
CK_IGNORE_UNUSED_VARIABLE_END

void init_capabilities(JNIEnv *env CK_UNUSED) {
}

void destroy_capabilities(JNIEnv *env CK_UNUSED) {
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    capabilities
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_capabilities(
                                                                                JNIEnv *env CK_UNUSED, jclass clazz CK_UNUSED)
{
    jint capabilities = 0;

#if !defined(_WIN32)
        capabilities |= CAPABILITY_UNSAFE;
#endif

    if(supportsUNIX()) {
        capabilities |= CAPABILITY_UNIX_DOMAIN;

#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) || \
defined(SO_PEERCRED) || defined(SO_PEERID) || defined(__NetBSD__) || defined(__sun) || defined(__sun__) || defined(SIO_AF_UNIX_GETPEERPID)
#if defined(_OS400)
        // SO_PEERID appears to be not implemented
#else
        capabilities |= CAPABILITY_PEER_CREDENTIALS;
#endif
#endif

#if defined(junixsocket_have_ancillary)
        capabilities |= CAPABILITY_ANCILLARY_MESSAGES;
        capabilities |= CAPABILITY_FILE_DESCRIPTORS;
#endif

#if defined(__linux__)
        // despite earlier claims [1], it's not supported in Windows 10 (yet) [2]
        // [1] https://devblogs.microsoft.com/commandline/af_unix-comes-to-windows/
        // [2] https://github.com/microsoft/WSL/issues/4240
        capabilities |= CAPABILITY_ABSTRACT_NAMESPACE;
#endif

#if !defined(_WIN32)
        capabilities |= CAPABILITY_UNIX_DATAGRAMS;
#endif

#if !defined(_WIN32)
        capabilities |= CAPABILITY_NATIVE_SOCKETPAIR;
#endif

    } // supportsUNIX()

    if(supportsCastAsRedirect()) {
        capabilities |= CAPABILITY_FD_AS_REDIRECT;
    }

    if(supportsTIPC()) {
        capabilities |= CAPABILITY_TIPC;
    }
    
    if(supportsVSOCK()) {
        capabilities |= CAPABILITY_VSOCK;

        if(supportsVSOCK_dgram()) {
            capabilities |= CAPABILITY_VSOCK_DGRAM;
        }
    }

    if(supportsZeroLengthSend()) {
        capabilities |= CAPABILITY_ZERO_LENGTH_SEND;
    }

    if(supportsLargePorts()) {
        capabilities |= CAPABILITY_LARGE_PORTS;
    }

#if junixsocket_have_system
    capabilities |= CAPABILITY_DARWIN;
#endif
    return capabilities;
}
