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
#include "accept.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "address.h"
#include "poll.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    accept
 * Signature: ([BLjava/io/FileDescriptor;Ljava/io/FileDescriptor;JI)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_accept(
                                                                          JNIEnv * env, jclass clazz CK_UNUSED, jbyteArray addr, jobject fdServer,
                                                                          jobject fd, jlong expectedInode, int timeout)
{
    CK_ARGUMENT_POTENTIALLY_UNUSED(timeout);

    struct sockaddr_un su;
    socklen_t suLength = initSu(env, &su, addr);
    if(suLength == 0) return;

    int serverHandle = _getFD(env, fdServer);

    if(expectedInode > 0) {
        struct stat fdStat;

        // It's OK when the file's gone, but not OK if it refers to another inode.
        int statRes = stat(su.sun_path, &fdStat);
        if(statRes == 0) {
            ino_t statInode = fdStat.st_ino;

            if(statInode != (ino_t)expectedInode) {
                // inode mismatch -> someone else took over this socket address
                _closeFd(env, fdServer, serverHandle);
                _throwErrnumException(env,
                                      ECONNABORTED, NULL);
                return;
            }
        }
    }

#if defined(junixsocket_use_poll_for_accept)
    {
        int ret = pollWithTimeout(env, fdServer, serverHandle, timeout);
        if(ret == 0) {
            _throwErrnumException(env, ETIMEDOUT, fdServer);
            return;
        } else if(ret < 0) {
            return;
        }
    }
#endif

    int socketHandle;
    int errnum = 0;
    do {
        socketHandle = accept(serverHandle, (struct sockaddr *)&su, &suLength);
    } while(socketHandle == -1 && (errnum = socket_errno) == EINTR);
    if(socketHandle < 0) {
        _throwErrnumException(env, errnum, fdServer);
        return;
    }

    _initFD(env, fd, socketHandle);
    return;
}
