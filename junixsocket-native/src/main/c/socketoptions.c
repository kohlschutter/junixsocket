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
#include "socketoptions.h"

#include "exceptions.h"
#include "filedescriptors.h"

static jint convertSocketOptionToNative(jint optID)
{
    switch(optID) {
        case 0x0008:
            return SO_KEEPALIVE;
        case 0x0080:
            return SO_LINGER;
        case 0x1005:
            return SO_SNDTIMEO;
        case 0x1006:
            return SO_RCVTIMEO;
        case 0x1002:
            return SO_RCVBUF;
        case 0x1001:
            return SO_SNDBUF;
        default:
            return -1;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;I)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getSocketOptionInt(
                                                                                      JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint optID)
{
    int handle = _getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket option");
        return -1;
    }
#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
        struct timeval optVal;
        socklen_t optLen = sizeof(optVal);
        int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
        if(ret == -1) {
            _throwSockoptErrnumException(env, socket_errno, fd);
            return -1;
        }
        return (jint)(optVal.tv_sec * 1000 + optVal.tv_usec / 1000);
    } else
#endif
        if(optID == SO_LINGER) {
            struct linger optVal;
            socklen_t optLen = sizeof(optVal);

            int ret = getsockopt(handle, SOL_SOCKET, optID,
                                 WIN32_NEEDS_CHARP &optVal, &optLen);
            if(ret == -1) {
                _throwSockoptErrnumException(env, socket_errno, fd);
                return -1;
            }
            if(optVal.l_onoff == 0) {
                return -1;
            } else {
                return optVal.l_linger;
            }
        }

    int optVal;
    socklen_t optLen = sizeof(optVal);

    int ret = getsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
                         &optLen);
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
        return -1;
    }

    return optVal;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;II)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setSocketOptionInt(
                                                                                      JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint optID, jint value)
{
    int handle = _getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket option");
        return;
    }

#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
        // NOTE: SO_RCVTIMEO == SocketOptions.SO_TIMEOUT = 0x1006
        struct timeval optVal;
        optVal.tv_sec = value / 1000;
        optVal.tv_usec = (value % 1000) * 1000;
        int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal,
                             sizeof(optVal));

        if(ret == -1) {
            _throwSockoptErrnumException(env, socket_errno, fd);
            return;
        }
        return;
    } else
#endif
        if(optID == SO_LINGER) {
            struct linger optVal;

            optVal.l_onoff = value >= 0;
            optVal.l_linger = value >= 0 ? value : 0;

            int ret = setsockopt(handle, SOL_SOCKET, optID,
                                 WIN32_NEEDS_CHARP &optVal, sizeof(optVal));
            if(ret == -1) {
                _throwSockoptErrnumException(env, socket_errno, fd);
                return;
            }
            return;
        }

    int optVal = (int)value;

    int ret = setsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
                         sizeof(optVal));
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
        return;
    }
}
