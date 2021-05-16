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

// see AFUNIXSocketCapability.java in junixsocket-common
#pragma GCC diagnostic push
#pragma GCC diagnostic ignored "-Wunused-variable"
static int CAPABILITY_PEER_CREDENTIALS = (1 << 0);
static int CAPABILITY_ANCILLARY_MESSAGES = (1 << 1);
static int CAPABILITY_FILE_DESCRIPTORS = (1 << 2);
static int CAPABILITY_ABSTRACT_NAMESPACE = (1 << 3);
#pragma GCC diagnostic pop

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    capabilities
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_capabilities(
                                                                                JNIEnv *env CK_UNUSED, jclass clazz CK_UNUSED)
{

    int capabilities = 0;

#if defined(LOCAL_PEERCRED) || defined(LOCAL_PEEREPID) || defined(LOCAL_PEEREUUID) || defined(SO_PEERCRED)
    capabilities |= CAPABILITY_PEER_CREDENTIALS;
#endif

#if defined(junixsocket_have_ancillary)
    capabilities |= CAPABILITY_ANCILLARY_MESSAGES;
    capabilities |= CAPABILITY_FILE_DESCRIPTORS;
#endif

#if defined(_WIN32) || defined(__linux__)
    capabilities |= CAPABILITY_ABSTRACT_NAMESPACE;
#endif

    return capabilities;
}
