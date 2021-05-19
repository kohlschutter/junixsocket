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
#include "receive.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "ancillary.h"
#include "jniutil.h"
#include "poll.h"

static int optToFlags(jint opt) {
    int flags = 0;
    if ((opt & (org_newsclub_net_unix_NativeUnixSocket_OPT_PEEK)) != 0) {
        flags |= MSG_PEEK;
    }
    return flags;
}


ssize_t recv_wrapper(int handle, jbyte *buf, jint length, struct sockaddr_un *senderBuf, socklen_t *senderBufLen, int opt) {

    int flags = optToFlags(opt);

    ssize_t count;
    do {
        if (senderBuf != NULL) {
            count = recvfrom(handle, (char*)buf, length, flags, (struct sockaddr *)senderBuf, senderBufLen);
        } else {
            count = recv(handle, (char*)buf, length, flags);
        }
    } while(count == (ssize_t)-1 &&
            (socket_errno == EINTR
             || ((errno == EAGAIN || errno == EWOULDBLOCK) &&
                 (opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) == 0)
             ));

    return count;
}

ssize_t recvmsg_wrapper(JNIEnv * env, int handle, jbyte *buf, jint length, jbyte *control, socklen_t controlLen, struct sockaddr_un *senderBuf, socklen_t *senderBufLen, int opt, jobject ancSupp) {
#if !defined(junixsocket_have_ancillary)
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(ancSupp);
    CK_ARGUMENT_POTENTIALLY_UNUSED(control);
    CK_ARGUMENT_POTENTIALLY_UNUSED(controlLen);
    return recv_wrapper(handle, buf, length, senderBuf, senderBufLen, opt);
#else
    if (control == NULL || controlLen == 0 || ancSupp == NULL) {
        return recv_wrapper(handle, buf, length, senderBuf, senderBufLen, opt);
    }

    int flags = optToFlags(opt);

    ssize_t count;

    struct iovec iov = {.iov_base = buf, .iov_len = (size_t)length};
    struct msghdr msg = {.msg_name = (struct sockaddr*)senderBuf, .msg_namelen = senderBufLen == NULL ? 0 : *senderBufLen, .msg_iov = &iov, .msg_iovlen = 1, .msg_control =
        control, .msg_controllen = controlLen, };

    do {
        count = recvmsg(handle, &msg, flags);
    } while(count == (ssize_t)-1 &&
            (socket_errno == EINTR
#if junixsocket_have_MSG_DONTWAIT
             || ((errno == EAGAIN || errno == EWOULDBLOCK) &&
                 (opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) == 0)
#endif
             ));

    if (senderBufLen != 0) {
        *senderBufLen = msg.msg_namelen;
    }

    if((msg.msg_flags & MSG_CTRUNC) != 0) {
        errno = ENOBUFS;
        count = -1;
        return count;
    }

    controlLen = msg.msg_controllen;
    control = msg.msg_control;

    if(controlLen <= 0 || control == NULL || ancSupp == NULL) {
        return count;
    }

    for(struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg); cmsg != NULL; cmsg =
        junixsocket_CMSG_NXTHDR(&msg, cmsg)) {
        if(cmsg->cmsg_level == SOL_SOCKET
           && cmsg->cmsg_type == SCM_RIGHTS) {
            char *endBytes = (char*)cmsg + cmsg->cmsg_len;
            char *controlEnd = (char*)control + controlLen;
            if(controlEnd < endBytes) {
                endBytes = controlEnd;
            }

            unsigned char *data = CMSG_DATA(cmsg);
            unsigned char *end = (unsigned char *)endBytes;
            int numFds = (int)(end - data) / sizeof(int);

            CK_STATIC_ASSERT(sizeof(int)==sizeof(jint));

            if(numFds > 0) {
                jintArray fdArray = (*env)->NewIntArray(env, numFds);
                jint *fdBuf = (*env)->GetIntArrayElements(env, fdArray,
                                                          NULL);

                memcpy(fdBuf, data, numFds * sizeof(int));

                (*env)->ReleaseIntArrayElements(env, fdArray, fdBuf, 0);

                callObjectSetter(env, ancSupp, "receiveFileDescriptors",
                                 "([I)V", fdArray);
            }
        } else {
#if DEBUG
            fprintf(stderr, "NativeUnixSocket_read: Unexpected cmsg level:%i type:%i\n", cmsg->cmsg_level, cmsg->cmsg_type);
            fflush(stderr);
#endif
        }
    }

    return count;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    read
 * Signature: (Ljava/io/FileDescriptor;[BIILorg/newsclub/net/unix/AncillaryDataSupport;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_read(
                                                                        JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jbyteArray jbuf,
                                                                        jint offset, jint length, jobject ancSupp)
{
#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(ancSupp);
#endif

    // Performance: In order to read a single byte, simply don't specify a receive buffer.
    if(jbuf) {
        jsize bufLen = (*env)->GetArrayLength(env, jbuf);
        if(offset < 0 || length < 0 || offset >= bufLen) {
            _throwException(env, kExceptionSocketException, "Illegal offset or length");
            return -1;
        }

        jint maxRead = bufLen - offset;
        if(length > maxRead) {
            length = maxRead;
        }
    } else if(length != 1) {
        _throwException(env, kExceptionIndexOutOfBoundsException, "Illegal length");
        return -1;
    }

    int handle = _getFD(env, fd);

#if defined(junixsocket_use_poll_for_read)
    int ret = pollWithTimeout(env, fd, handle, 0);
    if(ret < 1) {
        return -1;
    }
#endif

    jbyte *buf = malloc((size_t)length);
    if(buf == NULL) {
        return -1; // OOME
    }

    ssize_t count;

    socklen_t controlLen;
#if !defined(junixsocket_have_ancillary)
    ancSupp = NULL;
    controlLen = 0;
#else
    jobject ancBuf;

    if(ancSupp != NULL) {
        ancBuf = (*env)->GetObjectField(env, ancSupp, getFieldID_ancillaryReceiveBuffer());

        if(ancBuf != NULL) {
            controlLen = (socklen_t)(*env)->GetDirectBufferCapacity(env, ancBuf);
        } else {
            controlLen = 0;
        }
    } else {
        controlLen = 0;
        ancBuf = NULL;
    }
#endif

    int flags = 0;

    jbyte *control;
#if defined(junixsocket_have_ancillary)
    control = ancBuf == NULL ? NULL : (*env)->GetDirectBufferAddress(env, ancBuf);
#else
    control = NULL;
#endif
    count = recvmsg_wrapper(env, handle, buf, length, control, controlLen, NULL, 0, flags, ancSupp);

    jint returnValue;
    if(count < 0) {
        // read(2) returns -1 on error. Java throws an Exception.
        _throwErrnumException(env, errno, fd);
        returnValue = -1;
    } else if(count == 0) {
        // read(2)/recv return 0 on EOF. Java returns -1.
        returnValue = -1;
    } else if(jbuf) {
        (*env)->SetByteArrayRegion(env, jbuf, offset, length, buf);

        returnValue = (jint)count;
    } else {
        // Directly return the byte we just read.
        returnValue = (*buf & 0xFF);
    }

    free(buf);
    return returnValue;
}
