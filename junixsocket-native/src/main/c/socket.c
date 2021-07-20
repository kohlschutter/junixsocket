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

int sockTypeToNative(JNIEnv *env, int type) {
    switch(type) {
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_STREAM:
            return SOCK_STREAM;
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_DGRAM:
            return SOCK_DGRAM;
        case org_newsclub_net_unix_NativeUnixSocket_SOCK_SEQPACKET:
            return SOCK_SEQPACKET;
        default:
            _throwException(env, kExceptionSocketException, "Illegal type");
            return -1;
    }
}
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

    type = sockTypeToNative(env, type);
    if(type == -1) {
        return;
    }

#if defined(junixsocket_have_socket_cloexec)
    handle = socket(AF_UNIX, type | SOCK_CLOEXEC, 0);
    if(handle == -1 && errno == EPROTONOSUPPORT) {
        handle = socket(AF_UNIX, type, 0);
        if(handle > 0) {
#  if defined(FD_CLOEXEC)
            fcntl(handle, F_SETFD, FD_CLOEXEC); // best effort
#  endif
        }
    }
#else
    handle = socket(AF_UNIX, type, 0);
#endif
    if(handle <= 0) {
        _throwErrnumException(env, socket_errno, fd);
        return;
    }

#if !defined(junixsocket_have_accept4)
#  if defined(FD_CLOEXEC)
    // macOS doesn't support SOCK_CLOEXEC
    fcntl(handle, F_SETFD, FD_CLOEXEC); // best effort
#  elif defined(_WIN32)
    // WSASocketW does not support AF_UNIX, so we can't set this atomically like on Linux
    HANDLE h = (HANDLE)_get_osfhandle(handle);
    SetHandleInformation(h, HANDLE_FLAG_INHERIT, 0); // best effort
#  endif
#endif

    _initFD(env, fd, handle);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    socketStatus
 * Signature: (Ljava/io/FileDescriptor;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_socketStatus
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd) {
     int handle = _getFD(env, fd);
     if(handle <= 0) {
         return org_newsclub_net_unix_NativeUnixSocket_SOCKETSTATUS_INVALID;
     }
     struct sockaddr_un addr = {0};
     socklen_t len = sizeof(struct sockaddr_un);

     int ret;

     ret = getpeername(handle, (struct sockaddr *)&addr, &len);
     if(ret != 0) {
         int errnum = socket_errno;
         switch(errnum) {
             case EINVAL:
             case ENOTCONN:
                 break;
             default:
                 _throwErrnumException(env, errnum, fd);
                 return -1;
         }
     } else {
         return org_newsclub_net_unix_NativeUnixSocket_SOCKETSTATUS_CONNECTED;
     }

     ret = getsockname(handle, (struct sockaddr *)&addr, &len);
     if(ret != 0) {
         int errnum = socket_errno;
         switch(errnum) {
             case EINVAL:
             case ENOTCONN:
                 break;
             default:
                 _throwErrnumException(env, errnum, fd);
                 return -1;
         }
     } else if (len > 0) {
         jboolean hasNonZero = false;

         len -= offsetof(struct sockaddr_un, sun_path);

         for(socklen_t i=0;i<len;i++) {
             if(addr.sun_path[i] != 0) {
                 hasNonZero = true;
                 break;
             }
         }
         if(hasNonZero) {
             return org_newsclub_net_unix_NativeUnixSocket_SOCKETSTATUS_BOUND;
         }
     }
     return org_newsclub_net_unix_NativeUnixSocket_SOCKETSTATUS_UNKNOWN;
 }
