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

    const char* socketFile = (char*)(void*)(*env)->GetByteArrayElements(env,
                                                                        addr, NULL);
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
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_maxAddressLength(
                                                                                    JNIEnv * env CK_UNUSED, jclass clazz CK_UNUSED)
{
    struct sockaddr_un su;
    return sizeof(su.sun_path);
}

