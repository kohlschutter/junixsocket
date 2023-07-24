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

#ifndef address_h
#define address_h

#include "config.h"

typedef union {
    struct sockaddr addr;
    struct sockaddr_un un;
#if junixsocket_have_tipc
    struct sockaddr_tipc tipc;
#endif
#if junixsocket_have_vsock
    struct sockaddr_vm vsock;
#endif
#if junixsocket_have_system
    struct sockaddr_ctl system;
#endif
    char bytes[128];
} jux_sockaddr_t;


socklen_t initSu(JNIEnv * env, struct sockaddr_un *su, jbyteArray addr);
int domainToNative(int domain);

void fixupSocketAddress(int handle, jux_sockaddr_t *sa, socklen_t addrLen);
bool fixupSocketAddressPostError(int handle, jux_sockaddr_t *sa, socklen_t addrLen, int errnum);

#endif /* address_h */
