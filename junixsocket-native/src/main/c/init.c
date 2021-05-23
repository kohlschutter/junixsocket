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
#include "init.h"

#include "exceptions.h"
#include "ancillary.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    init
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_init(
                                                                        JNIEnv *env, jclass clazz CK_UNUSED)
{
#if defined(_WIN32)
    WSADATA wsaData;
    int ret = WSAStartup(MAKEWORD(2,2), &wsaData);
    if(ret != 0) {
        _throwErrnumException(env, socket_errno, NULL);
        return;
    }
#endif

    init_filedescriptors(env);
#if defined(junixsocket_have_ancillary)
    init_ancillary(env);
#endif
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    destroy
 * Signature: ()V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_destroy(
                                                                           JNIEnv *env, jclass clazz CK_UNUSED)
{
#if defined(_WIN32)
    WSACleanup();
#endif

    destroy_filedescriptors(env);
#if defined(junixsocket_have_ancillary)
    destroy_ancillary(env);
#endif
}
