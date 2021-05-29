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
#include "connect.h"

#include "exceptions.h"
#include "address.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    connect
 * Signature: ([BLjava/io/FileDescriptor;J)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_connect(
                                                                           JNIEnv * env, jclass clazz CK_UNUSED, jbyteArray addr, jobject fd,
                                                                           jlong expectedInode)
{
    struct sockaddr_un su;
    socklen_t suLength = initSu(env, &su, addr);
    if(suLength == 0) return false;

    int socketHandle = _getFD(env, fd);
    if(socketHandle <= 0) {
        _throwException(env, kExceptionSocketException, "Socket closed");
        return false;
    }

    if(expectedInode > 0) {
        struct stat fdStat;

        // It's OK when the file's gone, but not OK if it refers to another inode.
        int statRes = stat(su.sun_path, &fdStat);
        if(statRes == 0) {
            ino_t statInode = fdStat.st_ino;

            if(statInode != (ino_t)expectedInode) {
                // inode mismatch -> someone else took over this socket address
                _throwErrnumException(env, ECONNABORTED, NULL);
                return false;
            }
        }
    }

    int errnum = errno;

    int ret;
    do {
        ret = connect(socketHandle, (struct sockaddr *)&su, suLength);
    } while(ret == -1 && (errnum = socket_errno) == EINTR);

    if(ret == -1) {
        if(checkNonBlocking(socketHandle, errnum)) {
            // non-blocking connect
            return false;
        } else {
            _throwErrnumException(env, errnum, NULL);
            return false;
        }
    }

    _initFD(env, fd, socketHandle);
    return true;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    disconnect
 * Signature: (Ljava/io/FileDescriptor;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_disconnect
(JNIEnv *env, jclass clazz CK_UNUSED, jobject fd) {
    int sockfd = _getFD(env, fd);
    if(sockfd <= 0) {
        _throwException(env, kExceptionSocketException, "Socket closed");
        return;
    }

    struct sockaddr_un sa_disconnect = {
        .sun_family = AF_UNSPEC,
        .sun_path = {0,0}
#ifdef junixsocket_have_sun_len
        , .sun_len=1
#endif
    };

    int ret = connect(sockfd, (struct sockaddr *)&sa_disconnect, sizeof(sa_disconnect));
    if (ret != 0) {
        int myErr = socket_errno;
        if (myErr != ENOENT && myErr != EAFNOSUPPORT) {
            _throwErrnumException(env, myErr, NULL);
        }
    }
    return;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    finishConnect
 * Signature: (Ljava/io/FileDescriptor;)Z
 */
JNIEXPORT jboolean JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_finishConnect
 (JNIEnv *env, jclass clazz CK_UNUSED, jobject fd) {
     int socketHandle = _getFD(env, fd);
     if(socketHandle <= 0) {
         _throwException(env, kExceptionSocketException, "Socket closed");
         return false;
     }

     jboolean success = false;

     struct pollfd* pollFd = calloc(1, sizeof(struct pollfd));
     pollFd[0].fd = socketHandle;
     pollFd[0].events = POLLOUT;

#if defined(_WIN32)
     int ret = WSAPoll(pollFd, 1, 0);
#else
     int ret = poll(pollFd, 1, 0);
#endif
     if(ret < 0) {
         _throwSockoptErrnumException(env, socket_errno, NULL);
         goto end;
     } else if(ret == 0) {
         goto end;
     }

     int result = 0;
     socklen_t resultLen = sizeof(result);
     ret = getsockopt(socketHandle, SOL_SOCKET, SO_ERROR, (void*)&result, &resultLen);
     if(ret != 0) {
         if (socket_errno == EINPROGRESS) {
             goto end;
         }
         _throwSockoptErrnumException(env, socket_errno, NULL);
         goto end;
     } else if(result != 0) {
         _throwSockoptErrnumException(env, result, NULL);
         goto end;
     }

     struct sockaddr_un addr = {0};

     socklen_t addrSize = sizeof(struct sockaddr_un);
     ret = getpeername(socketHandle, (struct sockaddr *)&addr, &addrSize);
     if(ret != 0) {
         // not connected, ignore error
         goto end;
     }

     success = true;
 end:
     free(pollFd);
     return success;
 }
