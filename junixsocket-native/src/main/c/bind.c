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
#include "vsock.h"
#include "socket.h"

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

    fixupSocketAddress(serverHandle, addr, suLength);

    if(
#if defined(_OS400) || __TOS_MVS__
       JNI_TRUE
#else
       addr->addr.sa_family != AF_UNIX
#endif
    ) {
        int errnum = 0;
        int bindRes = bind(serverHandle, &addr->addr, suLength);
        if(bindRes < 0) {
            errnum = socket_errno;
        }
        if(bindRes < 0 && fixupSocketAddressPostError(serverHandle, addr, suLength, errnum)) {
            // address fixed, try again
            bindRes = bind(serverHandle, &addr->addr, suLength);
            errnum = socket_errno;
        }
        if(bindRes < 0) {
            _throwErrnumException(env, errnum, NULL);
            return -1;
        }
        _initFD(env, fd, serverHandle);
        return 0;
    }

    bool reuse = ((options & org_newsclub_net_unix_NativeUnixSocket_BIND_OPT_REUSE) != 0) && addr->addr.sa_family == AF_UNIX;

#if defined(_WIN32)
    if(reuse && addr->addr.sa_family == AF_UNIX && addr->un.sun_path[0] != 0) {
        // Windows AF_UNIX does not terminate existing accept threads after another thread
        // has called bind/accept on the same unix socket.

        // Tell any (junixsocket) accept to close the serversocket
        // by renaming the unix socket file and connecting to it
        // see accept.c how we handle this.

        char tempFileName[MAX_PATH + 1] = ".";
        int len = strnlen((const char *)addr->un.sun_path, MIN(MAX_PATH, sizeof(struct sockaddr_un) - 2));

        int lastSlash = -1;
        for(int i = len-1; i >= 0; i--) {
            switch(addr->un.sun_path[i]) {
                case '\\':
                case '/':
                    lastSlash = i;
                    i = -1;
                    break;
            }
        }
        if(lastSlash > 0) {
            memcpy(&tempFileName, addr->un.sun_path, lastSlash);
        }

        if(GetTempFileNameA((const char *)&tempFileName, "jux", 0, (char *)&tempFileName) != 0) {
            DeleteFileA((const char *)&tempFileName);

            if(rename(addr->un.sun_path, (const char *)&tempFileName) == 0) {
                struct sockaddr_un addr2 = { 0 };
                addr2.sun_family = AF_UNIX;
                memcpy(&(addr2.sun_path), &tempFileName,
                       strnlen((const char *)&tempFileName, sizeof(struct sockaddr_un)-1));

                int hnd = socket(AF_UNIX, SOCK_STREAM, 0);
                int ret;
                do {
                    ret = connect(hnd, (struct sockaddr*)&addr2, sizeof(struct sockaddr_un));
                } while(ret == -1 && (errno = socket_errno) == EINTR);
                closesocket(hnd);
            } else {
                // rename failed -- old accept may still hang
            }
            DeleteFileA((const char *)&tempFileName);
        } else {
            // couldn't create temporary file -- old accept may still hang
        }
        DeleteFileA((const char *)addr->un.sun_path);
    }

    int bindRes = bind(serverHandle, (struct sockaddr *)&addr->addr, suLength);
    int myErr = bindRes == 0 ? 0 : socket_errno;
    _initFD(env, fd, serverHandle);

    if(bindRes < 0) {
        _throwErrnumException(env, myErr, NULL);
        return -1;
    } else if(addr->addr.sa_family == AF_UNIX && addr->un.sun_path[0] != 0) {
        // Update creation time of the socket file; Windows apparently doesn't do that!

        HANDLE h = CreateFileA(addr->un.sun_path, FILE_WRITE_ATTRIBUTES,
                               FILE_SHARE_READ | FILE_SHARE_WRITE,
                               NULL,
                               OPEN_EXISTING, FILE_FLAG_BACKUP_SEMANTICS | FILE_FLAG_OPEN_REPARSE_POINT,
                               0);
        if(h != INVALID_HANDLE_VALUE) {
            FILETIME fTime;
            if(GetFileTime(h, NULL, NULL, &fTime)) {
                SetFileTime(h, &fTime, NULL, NULL);
            }

            CloseHandle(h);
        }

        jlong inode = getInodeIdentifier(addr->un.sun_path);
        if(inode == -1) {
            _throwErrnumException(env, errno, NULL);
            return -1;
        }
        return inode;
    } else {
        return 0;
    }
#else
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

    jlong inode;
    if(addr->addr.sa_family != AF_UNIX) {
        inode = 0;
    } else if(addr->un.sun_path[0] == 0) {
        // no inodes in the abstract namespace
        inode = 0;
    } else {
        inode = getInodeIdentifier(addr->un.sun_path);
        if(inode == -1) {
            _throwErrnumException(env, errno, NULL);
            return -1;
        }
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
