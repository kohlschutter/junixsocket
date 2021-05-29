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
#include "address.h"

#include "exceptions.h"
#include "filedescriptors.h"
#include "jniutil.h"

// Either we have sun_len (and we can skip the null byte), or we add the null byte at the end
static const socklen_t SUN_NAME_MAX_LEN = (socklen_t)(sizeof(struct sockaddr_un) - 2);

/**
 * Initializes a sockaddr_un given a byte[] address, returning the socklen,
 * or 0 if an error occurred.
 */
socklen_t initSu(JNIEnv * env, struct sockaddr_un *su, jbyteArray addr) {
    const int maxLen = sizeof(su->sun_path);

    socklen_t addrLen = (socklen_t)(*env)->GetArrayLength(env, addr);
    if((int)addrLen <= 0 || addrLen >= maxLen) {
        _throwException(env, kExceptionSocketException,
                        "Socket address length out of range");
        return 0;
    }

    const char* socketFile = (char*)(void*)(*env)->GetByteArrayElements(env, addr, NULL);
    if(socketFile == NULL) {
        return 0; // OOME
    }

    su->sun_family = AF_UNIX;
    memset(su->sun_path, 0, maxLen);
    memcpy(su->sun_path, socketFile, addrLen);

    (*env)->ReleaseByteArrayElements(env, addr, (jbyte*)(void*)socketFile, 0);
    socketFile = NULL;

#if defined(junixsocket_have_sun_len)
    su->sun_len = (unsigned char)(sizeof(*su) - sizeof(su->sun_path) + addrLen);
#endif

    socklen_t suLength = (socklen_t)(addrLen + sizeof(su->sun_family)
#if defined(junixsocket_have_sun_len)
                                     + sizeof(su->sun_len)
#endif
                                     );

    return suLength;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    maxAddressLength
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_maxAddressLength
(JNIEnv * env CK_UNUSED, jclass clazz CK_UNUSED)
{
    static struct sockaddr_un su;
    return sizeof(su.sun_path);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sockAddrUnLength
 * Signature: ()I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sockAddrUnLength
(JNIEnv * env CK_UNUSED, jclass clazz CK_UNUSED)
{
    return sizeof(struct sockaddr_un);
}

static jbyteArray sockAddrUnToBytes(JNIEnv *env, struct sockaddr_un *addr, socklen_t len) {
#if defined(junixsocket_have_sun_len)
    if(addr->sun_len < len) {
        len = addr->sun_len;
    }
#endif
    int terminator = -1;
    jboolean firstZero = (addr->sun_path[0] == 0);
    jboolean allZeros = firstZero;

    for(socklen_t i=1; i<len; i++) {
        char c = addr->sun_path[i];
        if(c == 0) {
            if(!firstZero && terminator == -1) {
                terminator = i;
                len = i;
            }
        } else {
            if(firstZero || terminator == -1) {
                allZeros = false;
            }
        }
    }

    if(allZeros) {
        len = 0;
    }

    if(len == 0) {
        return NULL;
    }

    jbyteArray array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, array, 0, len, (jbyte*)addr->sun_path);

    return array;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sockname
 * Signature: (Ljava/io/FileDescriptor;Z)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sockname
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jboolean peerName) {
    int handle = _getFD(env, fd);

    struct sockaddr_un addr = {0};

    socklen_t len = sizeof(struct sockaddr_un);
    int ret;
    if (peerName) {
        ret = getpeername(handle, (struct sockaddr *)&addr, &len);
    } else {
        ret = getsockname(handle, (struct sockaddr *)&addr, &len);
    }
    if(ret != 0) {
        int errnum = socket_errno;
        _throwErrnumException(env, errnum, fd);
        return NULL;
    }

    if(len > (socklen_t)sizeof(struct sockaddr_un)) {
        _throwException(env, kExceptionSocketException,
                        peerName ? "peer name too long" : "socket name too long");
        return NULL;
    }

    if(((struct sockaddr *)&addr)->sa_family == AF_UNIX) {
#if defined(junixsocket_have_sun_len)
        len -= 2;
#else
        len -= 1;
#endif
        return sockAddrUnToBytes(env, (struct sockaddr_un *)&addr, len);
#if defined(_WIN32)
    } else if(((struct sockaddr *)&addr)->sa_family == AF_INET) {
        // only to support our "socketpair" workaround (which we expected to return NULL here on UNIX)
        return NULL;
#endif
    } else {
        _throwException(env, kExceptionSocketException,
                        "Unsupported socket family");
        return NULL;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    sockAddrUnToBytes
 * Signature: (Ljava/nio/ByteBuffer;)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sockAddrUnToBytes
(JNIEnv *env, jclass clazz CK_UNUSED, jobject directByteBuf) {
    struct jni_direct_byte_buffer_ref directByteBufRef =
    getDirectByteBufferRef (env, directByteBuf, 0, sizeof(struct sockaddr_un));
    if(directByteBufRef.size <= 0) {
        _throwException(env, kExceptionSocketException, "Invalid byte buffer");
        return NULL;
    }

    struct sockaddr_un *addr = (struct sockaddr_un *)directByteBufRef.buf;
    return sockAddrUnToBytes(env, addr, SUN_NAME_MAX_LEN);
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    bytesToSockAddrUn
 * Signature: (Ljava/nio/ByteBuffer;[B)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_bytesToSockAddrUn
(JNIEnv *env, jclass clazz CK_UNUSED, jobject directByteBuf, jbyteArray addressBytes) {
    struct jni_direct_byte_buffer_ref directByteBufRef =
    getDirectByteBufferRef (env, directByteBuf, 0, sizeof(struct sockaddr_un));
    if(directByteBufRef.size <= 0) {
        _throwException(env, kExceptionSocketException, "Invalid byte buffer");
        return;
    }

    jlong len = addressBytes == NULL ? 0 : (*env)->GetArrayLength(env, addressBytes);
    if (len > directByteBufRef.size) {
        _throwException(env, kExceptionSocketException, "Byte array is too large");
        return;
    }

    struct sockaddr_un *addr = (struct sockaddr_un *)directByteBufRef.buf;
    memset(directByteBufRef.buf, 0, sizeof(struct sockaddr_un));
    addr->sun_family = AF_UNIX;

    if (len > 0) {
#if defined(junixsocket_have_sun_len)
        addr->sun_len = (len >= SUN_NAME_MAX_LEN) ? SUN_NAME_MAX_LEN : len + 1; // including zero byte
#endif
        (*env)->GetByteArrayRegion(env, addressBytes, 0, len, (signed char*)addr->sun_path);
    }
}
