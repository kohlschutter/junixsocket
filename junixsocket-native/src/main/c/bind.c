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
#include "bind.h"

#include "exceptions.h"
#include "address.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    bind
 * Signature: (Ljava/nio/ByteBuffer;ILjava/io/FileDescriptor;I)J
 */
JNIEXPORT jlong JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_bind
 (JNIEnv * env, jclass clazz CK_UNUSED, jobject ab, jint abLen, jobject fd, jint options)
{
#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(options);
#endif

    // FIXME domain

    jux_sockaddr_t *addr = (*env)->GetDirectBufferAddress(env, ab);
    socklen_t suLength = (socklen_t)abLen;

    int serverHandle = _getFD(env, fd);
    if(serverHandle < 0) {
        _throwException(env, kExceptionSocketException, "Socket is closed");
        return -1;
    }

    if(suLength == 0) {
        // unbind / anonymous bind
        int bindRes = bind(serverHandle, &addr->addr, 0);
        if(bindRes < 0) {
            _throwErrnumException(env, socket_errno, NULL);
            return -1;
        }
        _initFD(env, fd, serverHandle);
        return 0;
    }

    if(
#if defined(_OS400) || __TOS_MVS__
       JNI_TRUE
#else
       addr->addr.sa_family != AF_UNIX
#endif
    ) {
        int bindRes = bind(serverHandle, &addr->addr, suLength);
        int myErr = socket_errno;
        if(bindRes < 0) {
            _throwErrnumException(env, myErr, NULL);
            return -1;
        }
        _initFD(env, fd, serverHandle);
        return 0;
    }

#if defined(_WIN32)
    if(addr->addr.sa_family == AF_UNIX && addr->un.sun_path[0] != 0) {
        DeleteFileA(addr->un.sun_path);
    }

    int bindRes;

    bindRes = bind(serverHandle, (struct sockaddr *)&addr->addr, suLength);
    int myErr = socket_errno;
    _initFD(env, fd, serverHandle);

    if(bindRes < 0) {
        _throwErrnumException(env, myErr, NULL);
        return -1;
    } else {
        return 0;
    }
#else
    bool reuse = ((options & org_newsclub_net_unix_NativeUnixSocket_BIND_OPT_REUSE) != 0) && addr->addr.sa_family == AF_UNIX;
    bool useSuTmp = false;
    struct sockaddr_un suTmp;
    if(reuse) {
        suTmp.sun_family = AF_UNIX;
        suTmp.sun_path[0] = 0;
#ifdef junixsocket_have_sun_len
        suTmp.sun_len = (unsigned char)(sizeof(suTmp) - sizeof(suTmp.sun_path)
                                        + strlen(suTmp.sun_path));
#endif
    }

    int myErr;

    for(int attempt = 0; attempt < 2; attempt++) {
#if CK_EXCLUDED_FROM_STATIC_ANALYSIS
        myErr = 0;
#endif

        int ret;
        int optVal = 1;

        if(reuse) {
            // reuse address

            // This block is only prophylactic, as SO_REUSEADDR seems not to affect AF_UNIX sockets
            ret = setsockopt(serverHandle, SOL_SOCKET, SO_REUSEADDR, &optVal,
                             sizeof(optVal));
            if(ret == -1) {
                _throwSockoptErrnumException(env, socket_errno, fd);
                return -1;
            }
        }

#if defined(SO_NOSIGPIPE)
        // prevent raising SIGPIPE
        ret = setsockopt(serverHandle, SOL_SOCKET, SO_NOSIGPIPE, &optVal, sizeof(optVal));
        if(ret == -1) {

            _throwSockoptErrnumException(env, socket_errno, fd);
            return -1;
        }
#endif
        int bindRes;

        if(attempt == 0 && !reuse && addr->addr.sa_family == AF_UNIX) {
            // if we're not going to reuse the socket, let's try to connect first.
            // This avoids changing file metadata (e.g. ctime!)
            bindRes = -1;
            errno = 0;
        } else {
            bindRes = bind(serverHandle, &addr->addr, suLength);
        }

        myErr = socket_errno;

        if(bindRes == 0) {
            break;
        } else if(attempt == 0 && (!reuse || myErr == EADDRINUSE)) {
            if(reuse) {
                if(addr->un.sun_path[0] == 0) {
                    // nothing to be done in the abstract namespace
                } else {
                    // if we're reusing the socket, it's better to move away the existing
                    // socket, bind ours to the correct address, and then connect to
                    // the temporary file (to unblock the accept), and finally delete
                    // the temporary file
                    strcpy(suTmp.sun_path, "/tmp/junixsocket.XXXXXX\0");
                    mkstemp(suTmp.sun_path);

                    int renameRet = rename(addr->un.sun_path, suTmp.sun_path);
                    if(renameRet == -1) {
                        if(socket_errno != ENOENT) {
                            // ignore failure
                        }
                    } else {
                        useSuTmp = true;
                    }
                }
            }

            if(useSuTmp) {
                // we've moved the existing socket, let's try again!
                continue;
            }

            errno = 0;

            // if the given file exists, but is not a socket, ENOTSOCK is returned
            // if access is denied, EACCESS is returned
            do {
                ret = connect(serverHandle, &addr->addr, suLength);
            } while(ret == -1 && (errno = socket_errno) == EINTR);

            if(ret == 0) {
                // if we can successfully connect, the address is in use
                errno = EADDRINUSE;
            } else if(errno == ENOENT) {
                continue;
            }

            if(ret == 0
               || (ret == -1
                   && (errno == ECONNREFUSED || errno == EADDRINUSE))) {
                // assume existing socket file

                if(reuse || errno == ECONNREFUSED) {
                    // either reuse existing socket, or take over a no longer working socket
                    if(addr->un.sun_path[0] == 0) {
                        // no need to unlink in the abstract namespace
                        continue;
                    } else if(unlink(addr->un.sun_path) == -1) {
                        if(errno == ENOENT) {
                            continue;
                        }
                    } else {
                        continue;
                    }
                }
            }
        }

        _throwErrnumException(env, errno, NULL);
        return -1;
    }

    if(addr->addr.sa_family != AF_UNIX) {
        // nothing to do
    } else if(addr->un.sun_path[0] == 0) {
        // nothing to be done for the abstract namespace
    } else {
#if !defined(_WIN32)
        int chmodRes = chmod(addr->un.sun_path, 0666);
        if(chmodRes == -1) {
            myErr = errno;
            _throwErrnumException(env, myErr, NULL);
            return -1;
        }
#endif
    }

    _initFD(env, fd, serverHandle);

    struct stat fdStat;
    ino_t inode;

    if(addr->addr.sa_family != AF_UNIX) {
        inode = 0;
    } else if(addr->un.sun_path[0] == 0) {
        // no inodes in the abstract namespace
        inode = 0;
    } else {
#if !defined(_WIN32)
        int statRes = stat(addr->un.sun_path, &fdStat);
        if(statRes == -1) {
            if (errno == EINVAL) {
                inode = 0;
            } else {
                myErr = errno;
                _throwErrnumException(env, myErr, NULL);
                return -1;
            }
        } else {
            inode = fdStat.st_ino;
        }
#else
        inode = 0;
#endif
    }

    if(useSuTmp) {
        // now that we've bound our socket, let the previously listening server know.
        // We've moved their socket to a safe place, which we'll have to unlink, too!

        socklen_t suTmpLength = (socklen_t)(
                                            strlen(suTmp.sun_path) + sizeof(suTmp.sun_family)
#ifdef junixsocket_have_sun_len
                                            + (unsigned char)sizeof(suTmp.sun_len)
#endif
                                            );

        int tmpHandle = socket(PF_UNIX, SOCK_STREAM, 0);
        if(tmpHandle != -1) {
            int ret;
            do {
                ret = connect(tmpHandle, (struct sockaddr *)&suTmp,
                              suTmpLength);
            } while(ret == -1 && socket_errno == EINTR);

#if CK_EXCLUDED_FROM_STATIC_ANALYSIS
            // FIXME: do we need to check errors here?
            ret = shutdown(tmpHandle, SHUT_RDWR);
#   if defined(_WIN32)
            ret = closesocket(tmpHandle);
#   else
            ret = close(tmpHandle);
#   endif
#endif
        }

        if(suTmp.sun_path[0] == 0) {
            // no need to unlink in the abstract namespace
        } else if(unlink(suTmp.sun_path) == -1) {
            if(errno != ENOENT) {
                _throwErrnumException(env, errno, NULL);
                return -1;
            }
        }
    }

    return (jlong)inode;
#endif
}
