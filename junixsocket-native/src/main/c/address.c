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

#ifdef junixsocket_have_sun_len
    su->sun_len = (unsigned char)(sizeof(*su) - sizeof(su->sun_path) + addrLen);
#endif

    socklen_t suLength = (socklen_t)(addrLen + sizeof(su->sun_family)
#ifdef junixsocket_have_sun_len
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
 * Method:    sockname
 * Signature: (Ljava/io/FileDescriptor;Z)[B
 */
JNIEXPORT jbyteArray JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_sockname
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jboolean peerName) {
    int handle = _getFD(env, fd);

    struct sockaddr_un addr = {};

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

    // FIXME SUN_LEN?
    len -= 1;
#if defined(junixsocket_have_sun_len)
    len -= 1;
#endif

    jboolean allZeros = true;
    for(socklen_t i=0; i<len; i++) {
        if(addr.sun_path[i] != 0) {
            allZeros = false;
            break;
        }
    }
    if(allZeros) {
        return NULL;
    }

    jbyteArray array = (*env)->NewByteArray(env, len);
    (*env)->SetByteArrayRegion(env, array, 0, len, (jbyte*)addr.sun_path);

    return array;
}
