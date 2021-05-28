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

    int myErr = errno;

    int ret;
    do {
        ret = connect(socketHandle, (struct sockaddr *)&su, suLength);
    } while(ret == -1 && (myErr = socket_errno) == EINTR);

    if(ret == -1) {
        _throwErrnumException(env, myErr, NULL);
        return false;
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
