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
#include "socket.h"

#include "org_newsclub_net_unix_NativeUnixSocket.h"
#include "filedescriptors.h"
#include "exceptions.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    createSocket
 * Signature: (Ljava/io/FileDescriptor;I)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_createSocket
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint type) {
    int handle = _getFD(env, fd);
    if(handle > 0) {
        // already initialized
        _throwException(env, kExceptionSocketException, "Already created");
        return;
    }

    switch(type) {
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_STREAM:
            type = SOCK_STREAM;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_DGRAM:
            type = SOCK_DGRAM;
            break;
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_SEQPACKET:
            type = SOCK_SEQPACKET;
            break;
        default:
            _throwException(env, kExceptionSocketException, "Illegal type");
            return;
    }

    handle = socket(PF_UNIX, type, 0);
    if(handle <= 0) {
        _throwErrnumException(env, socket_errno, fd);
        return;
    }

    _initFD(env, fd, handle);
}
