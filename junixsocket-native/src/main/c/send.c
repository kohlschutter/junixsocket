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
#include "send.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "ancillary.h"
#include "receive.h"
#include "jniutil.h"

static jboolean sleepForRetryWriting() {
    usleep(1000); // 1 ms
    return true;
}

ssize_t send_wrapper(int handle, jbyte *buf, jint length, struct sockaddr_un *sendTo, socklen_t sendToLen, int opt) {
    ssize_t count;

    do {
        errno = 0;
        if (sendTo != NULL) {
            count = sendto(handle, (char*)buf, length, 0, (struct sockaddr *)sendTo, sendToLen);
        } else if((opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_SOCKET) != 0) {
            // "write" can be used with pipes, too.
            count = write(handle, (char*)buf, length);
        } else {
            count = send(handle, (char*)buf, length, 0);
        }
        // on macOS/BSD, send seems to not block if the send buffer is full, so we have to handle ENOBUFS
    } while(count == -1 && (socket_errno == EINTR ||
                            (
                             errno == ENOBUFS
                             && (opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) == 0
                             && sleepForRetryWriting()
                             )
                            ));
    return count;
}

ssize_t sendmsg_wrapper(JNIEnv * env, int handle, jbyte *buf, jint length, struct sockaddr_un *sendTo, socklen_t sendToLen, int opt, jobject ancSupp) {
#if !defined(junixsocket_have_ancillary)
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(ancSupp);
    return send_wrapper(handle, buf, length, sendTo, sendToLen, opt);
#else

    jintArray ancFds = ancSupp == NULL ? NULL : (*env)->GetObjectField(env, ancSupp, getFieldID_pendingFileDescriptors());
    if (ancFds == NULL) {
        return send_wrapper(handle, buf, length, sendTo, sendToLen, opt);
    }

    struct iovec iov = {.iov_base = buf, .iov_len = (size_t)length};
    struct msghdr msg = {.msg_name = (struct sockaddr*)sendTo, .msg_namelen =
        sendToLen, .msg_iov = &iov, .msg_iovlen = 1 };

    char *control = NULL;
    if(ancFds != NULL) {
        jsize ancFdsLen = (*env)->GetArrayLength(env, ancFds);
        msg.msg_controllen = (socklen_t)CMSG_SPACE((socklen_t)ancFdsLen * sizeof(jint));
        control = msg.msg_control = malloc(msg.msg_controllen);

        socklen_t controlLen = 0;
        struct cmsghdr *cmsg = CMSG_FIRSTHDR(&msg);
        cmsg->cmsg_level = SOL_SOCKET;
        cmsg->cmsg_type = SCM_RIGHTS;
        controlLen += (cmsg->cmsg_len = (socklen_t)CMSG_LEN((socklen_t)ancFdsLen * sizeof(jint)));
        unsigned char *data = CMSG_DATA(cmsg);

        jint *ancBuf = NULL;
        if(ancFdsLen > 0) {
            ancBuf = (*env)->GetIntArrayElements(env, ancFds, NULL);
            memcpy(data, ancBuf, ancFdsLen * sizeof(jint));
            (*env)->ReleaseIntArrayElements(env, ancFds, ancBuf, 0);
        }

        cmsg = junixsocket_CMSG_NXTHDR(&msg, cmsg);
        if(cmsg == NULL) {
            // FIXME: not enough space in header?
        }

        msg.msg_controllen = controlLen;

        (*env)->SetObjectField(env, ancSupp, getFieldID_pendingFileDescriptors(), NULL);
    }

    ssize_t count;

    errno = 0;
    do {
        if (msg.msg_controllen == 0) {
            count = send(handle, msg.msg_iov->iov_base, msg.msg_iov->iov_len, 0);
        } else {
            count = sendmsg(handle, &msg, 0);
        }
    } while(count == -1 && (socket_errno == EINTR ||
                            (
                             errno == ENOBUFS
                             && (opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) == 0
                             && sleepForRetryWriting()
                             )
                            ));

    if(control) {
        free(control);
    }

    return count;
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    write
 * Signature: (Ljava/io/FileDescriptor;[BIILorg/newsclub/net/unix/AncillaryDataSupport;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_write(
                                                                         JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jbyteArray jbuf,
                                                                         jint offset, jint length, jobject ancSupp)
{
#if defined(_WIN32)
    CK_ARGUMENT_POTENTIALLY_UNUSED(ancSupp);
#endif

    // Performance: In order to write a single byte, simply don't specify a receive buffer.
    // "offset" contains the byte to write, and length must be 1
    if(jbuf) {
        jsize bufLen = (*env)->GetArrayLength(env, jbuf);
        if(offset < 0 || length < 0 || (length > (bufLen - offset))) {
            _throwException(env, kExceptionIndexOutOfBoundsException, "Illegal offset or length");
            return -1;
        }
    } else if(length != 1) {
        _throwException(env, kExceptionIndexOutOfBoundsException, "Illegal length");
        return -1;
    }

    jbyte *buf = malloc(length);
    if(buf == NULL) {
        return -1; // OOME
    }

    if(jbuf) {
        (*env)->GetByteArrayRegion(env, jbuf, offset, length, buf);
    } else {
        *buf = (jbyte)offset;
    }

    int handle = _getFD(env, fd);

    ssize_t count;

#if defined(junixsocket_have_ancillary)
    count = sendmsg_wrapper(env, handle, buf, length, NULL, 0, 0, ancSupp);

#else
    errno = 0;
    do {
        count = send(handle, (char*)buf, (size_t)length, 0);
    } while(count == -1 && socket_errno == EINTR);
#endif

    free(buf);

    if(count == -1) {
        if(errno == EAGAIN || errno == EWOULDBLOCK) {
            return 0;
        }

        _throwErrnumException(env, errno, fd);
        return -1;
    }

    return (jint)count;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    send
 * Signature: (Ljava/io/FileDescriptor;Ljava/nio/ByteBuffer;IILjava/nio/ByteBuffer;ILorg/newsclub/net/unix/AncillaryDataSupport;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_send
(JNIEnv *env, jclass clazz CK_UNUSED, jobject fd, jobject buffer, jint offset, jint length, jobject addressBuffer, jint opt, jobject ancSupp) {
    int handle = _getFD(env, fd);
    if (handle <= 0) {
        _throwException(env, kExceptionSocketException, "Socket closed");
        return 0;
    }

    struct jni_direct_byte_buffer_ref dataBufferRef =
    getDirectByteBufferRef (env, buffer, offset, 0);
    if(dataBufferRef.size == -1) {
        _throwException(env, kExceptionSocketException, "Cannot get buffer");
        return -1;
    } else if(dataBufferRef.buf == NULL) {
        _throwException(env, kExceptionNullPointerException, "buffer");
        return -1;
    }
    if(dataBufferRef.size < length) {
        length = dataBufferRef.size;
    }

    struct jni_direct_byte_buffer_ref addressBufferRef =
    getDirectByteBufferRef (env, addressBuffer, 0, sizeof(struct sockaddr_un));
    if(addressBufferRef.size == -1) {
        _throwException(env, kExceptionSocketException, "Cannot get addressBuffer");
        return -1;
    }

    struct sockaddr_un *sendTo = (struct sockaddr_un *)(addressBufferRef.buf);
    socklen_t sendToLen = addressBufferRef.size;

    int ret = sendmsg_wrapper(env, handle, dataBufferRef.buf, length, sendTo, sendToLen, opt, ancSupp);

    if(ret < 0) {
        ret = 0;
        if(socket_errno != EAGAIN && errno != EWOULDBLOCK && (errno != ENOBUFS || (opt & org_newsclub_net_unix_NativeUnixSocket_OPT_NON_BLOCKING) == 0 )) {
            if(!(*env)->ExceptionCheck(env)) {
                _throwErrnumException(env, errno, fd);
            }
        }
    }

    return ret;
}
