/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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

#include "afsystem.h"

#include "jniutil.h"
#include "exceptions.h"
#include "filedescriptors.h"

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    systemResolveCtlId
 * Signature: (Ljava/lang/String;)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_systemResolveCtlId
(JNIEnv *env, jclass klazz CK_UNUSED, jobject fd, jstring ctlName) {
    CK_ARGUMENT_POTENTIALLY_UNUSED(env);
    CK_ARGUMENT_POTENTIALLY_UNUSED(fd);
    CK_ARGUMENT_POTENTIALLY_UNUSED(ctlName);

#if junixsocket_have_system

    struct ctl_info info = {0};

    if(jstring_to_char_if_possible(env, ctlName, 0, info.ctl_name, sizeof(info.ctl_name)) == NULL) {
        _throwErrnumException(env, EINVAL, NULL);
        return -1;
    }

    int fdHandle = _getFD(env, fd);

    if(ioctl(fdHandle, CTLIOCGINFO, &info) != 0) {
        _throwErrnumException(env, errno, NULL);
        return -1;
    }

    return info.ctl_id;

#else
    _throwException(env, kExceptionSocketException, "AF_SYSTEM is not supported");
    return -1;
#endif
}

