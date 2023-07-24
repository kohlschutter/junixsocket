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

#include "exceptions.h"
#include "filedescriptors.h"

#if junixsocket_have_system

static inline char* java_to_char(JNIEnv* env, jstring string) {
    jsize len = (*env)->GetStringLength(env, string);
    size_t bytes = (*env)->GetStringUTFLength(env, string);
    char* chars = (char*) malloc(bytes + 1);
    (*env)->GetStringUTFRegion(env, string, 0, len, chars);
    chars[bytes] = 0;
    return chars;
}

#endif

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

    char *name = java_to_char(env, ctlName);

    size_t nameLen = strlen(name);

    strlcpy(info.ctl_name, name, MIN(nameLen + 1, sizeof(info.ctl_name)));

    free(name);

//    (*env)->ReleaseStringUTFChars(env, ctlName, name);

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

