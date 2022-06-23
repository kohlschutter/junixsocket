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
#include "socketoptions.h"

#include "jniutil.h"
#include "exceptions.h"
#include "filedescriptors.h"

#if __TOS_MVS__
#  include <sys/time.h>
#endif

static jclass kIntegerClass;
static jmethodID kIntegerConstructor;
static jmethodID kIntegerIntValue;

static jclass kAFTIPCGroupRequestClass;
static jmethodID kAFTIPCGroupRequestFromNative;
static jmethodID kAFTIPCGroupRequestGetType;
static jmethodID kAFTIPCGroupRequestGetInstance;
static jmethodID kAFTIPCGroupRequestGetScopeId;
static jmethodID kAFTIPCGroupRequestGetFlagsValue;

void init_socketoptions(JNIEnv *env) {
    kIntegerClass = findClassAndGlobalRef(env, "java/lang/Integer");
    kIntegerConstructor = kIntegerClass == NULL ? NULL : (*env)->GetMethodID(env, kIntegerClass, "<init>", "(I)V");
    kIntegerIntValue = kIntegerClass == NULL ? NULL : (*env)->GetMethodID(env, kIntegerClass, "intValue", "()I");
    if(kIntegerConstructor == NULL || kIntegerIntValue == NULL) {
        releaseClassGlobalRef(env, kIntegerClass);
        kIntegerClass = NULL;
    }

    kAFTIPCGroupRequestClass = findClassAndGlobalRef0(env, "org/newsclub/net/unix/tipc/AFTIPCGroupRequest", true);
    kAFTIPCGroupRequestFromNative = kAFTIPCGroupRequestClass == NULL ? NULL : (*env)->GetStaticMethodID(env, kAFTIPCGroupRequestClass, "fromNative", "(IIII)Lorg/newsclub/net/unix/tipc/AFTIPCGroupRequest;");
    kAFTIPCGroupRequestGetType = kAFTIPCGroupRequestClass == NULL ? NULL : (*env)->GetMethodID(env, kAFTIPCGroupRequestClass, "getType", "()I");
    kAFTIPCGroupRequestGetInstance = kAFTIPCGroupRequestClass == NULL ? NULL : (*env)->GetMethodID(env, kAFTIPCGroupRequestClass, "getInstance", "()I");
    kAFTIPCGroupRequestGetScopeId = kAFTIPCGroupRequestClass == NULL ? NULL : (*env)->GetMethodID(env, kAFTIPCGroupRequestClass, "getScopeId", "()I");
    kAFTIPCGroupRequestGetFlagsValue = kAFTIPCGroupRequestClass == NULL ? NULL : (*env)->GetMethodID(env, kAFTIPCGroupRequestClass, "getFlagsValue", "()I");
    if(kAFTIPCGroupRequestGetType == NULL || kAFTIPCGroupRequestGetInstance == NULL || kAFTIPCGroupRequestGetScopeId == NULL || kAFTIPCGroupRequestGetFlagsValue == NULL) {
        releaseClassGlobalRef(env, kAFTIPCGroupRequestClass);
        kAFTIPCGroupRequestClass = NULL;
    }
}

void destroy_socketoptions(JNIEnv *env) {
    releaseClassGlobalRef(env, kIntegerClass);
    releaseClassGlobalRef(env, kAFTIPCGroupRequestClass);
}

static jint convertSocketOptionToNative(jint optID)
{
    switch(optID) {
        case 0x0008:
            return SO_KEEPALIVE;
        case 0x0080:
            return SO_LINGER;
        case 0x1005:
            return SO_SNDTIMEO;
        case 0x1006:
            return SO_RCVTIMEO;
        case 0x1002:
            return SO_RCVBUF;
        case 0x1001:
            return SO_SNDBUF;
        default:
            return -1;
    }
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;I)I
 */
JNIEXPORT jint JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getSocketOptionInt
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint optID)
{
    int handle = _getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket option");
        return -1;
    }
#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
#if __TOS_MVS__
        // Unsupported on z/OS
        return -1;
#endif
        struct timeval optVal;
        socklen_t optLen = sizeof(optVal);
        int ret = getsockopt(handle, SOL_SOCKET, optID, &optVal, &optLen);
        if(ret == -1) {
            _throwSockoptErrnumException(env, socket_errno, fd);
            return -1;
        }
        return (jint)(optVal.tv_sec * 1000 + optVal.tv_usec / 1000);
    } else
#endif
        if(optID == SO_LINGER) {
            struct linger optVal;
            socklen_t optLen = sizeof(optVal);

            int ret = getsockopt(handle, SOL_SOCKET, optID,
                                 WIN32_NEEDS_CHARP &optVal, &optLen);
            if(ret == -1) {
                _throwSockoptErrnumException(env, socket_errno, fd);
                return -1;
            }
            if(optVal.l_onoff == 0) {
                return -1;
            } else {
                return optVal.l_linger;
            }
        }

    int optVal;
    socklen_t optLen = sizeof(optVal);

    int ret = getsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
                         &optLen);
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
        return -1;
    }

    return optVal;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setSocketOptionInt
 * Signature: (Ljava/io/FileDescriptor;II)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setSocketOptionInt
(JNIEnv * env, jclass clazz CK_UNUSED, jobject fd, jint optID, jint value)
{
    int handle = _getFD(env, fd);

    optID = convertSocketOptionToNative(optID);
    if(optID == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket option");
        return;
    }

#if !defined(_WIN32)
    if(optID == SO_SNDTIMEO || optID == SO_RCVTIMEO) {
#if __TOS_MVS__
        // Unsupported on z/OS
        return;
#endif

        // NOTE: SO_RCVTIMEO == SocketOptions.SO_TIMEOUT = 0x1006
        struct timeval optVal;
        optVal.tv_sec = value / 1000;
        optVal.tv_usec = (value % 1000) * 1000;
        int ret = setsockopt(handle, SOL_SOCKET, optID, &optVal,
                             sizeof(optVal));

        if(ret == -1) {
            _throwSockoptErrnumException(env, socket_errno, fd);
            return;
        }
        return;
    } else
#endif
        if(optID == SO_LINGER) {
            struct linger optVal;

            optVal.l_onoff = value >= 0;
            optVal.l_linger = value >= 0 ? value : 0;

            int ret = setsockopt(handle, SOL_SOCKET, optID,
                                 WIN32_NEEDS_CHARP &optVal, sizeof(optVal));
            if(ret == -1) {
                _throwSockoptErrnumException(env, socket_errno, fd);
                return;
            }
            return;
        }

    int optVal = (int)value;

    int ret = setsockopt(handle, SOL_SOCKET, optID, WIN32_NEEDS_CHARP &optVal,
                         sizeof(optVal));
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
        return;
    }
}

static jint optionDomainToNative(jint domain) {
    switch(domain) {
#if junixsocket_have_tipc
        case 271:
            return SOL_TIPC;
#endif
        default:
            return -1;
    }
}

static jint optionNameToNative(jint domain, jint optionName) {
    switch(domain) {
        case 271: // TIPC
            if(optionName >= 127 && optionName <= 138) {
                // allow compiling with older kernel headers by not using constants here
                // this should be relatively safe since TIPC is Linux-only for us right now.
                return optionName;
            }
            return -1;
        default:
            return -1;
    }
}

static jobject intToInteger(JNIEnv *env, void* valPtr, socklen_t valLen) {
    if(valLen < (signed)sizeof(jint)) {
        return NULL;
    }
    jint val = *((jint*)valPtr);
    return (*env)->NewObject(env, kIntegerClass, kIntegerConstructor, val);
}

static jboolean integerToInt(JNIEnv *env, jobject val, void* out) {
    *(jint*)out = (jint)((*env)->CallIntMethod(env, val, kIntegerIntValue));
    return JNI_TRUE;
}


#if junixsocket_have_tipc
#  if !defined(TIPC_GROUP_JOIN)
struct tipc_group_req {
    __u32 type;
    __u32 instance;
    __u32 scope;
    __u32 flags;
};
#  endif

static jobject groupReqToJava(JNIEnv *env, void* valPtr, socklen_t valLen) {
    if(valLen < sizeof(struct tipc_group_req)) {
        if(valLen == sizeof(jint)) {
            // response is just the group type
            return (*env)->CallStaticObjectMethod(env, kAFTIPCGroupRequestClass, kAFTIPCGroupRequestFromNative, *((jint*)valPtr),0,0,0);
        }
        return NULL;
    }
    struct tipc_group_req gr = *((struct tipc_group_req *)valPtr);
    return (*env)->CallStaticObjectMethod(env, kAFTIPCGroupRequestClass, kAFTIPCGroupRequestFromNative, gr.type, gr.instance, gr.scope, gr.flags);
}

static jboolean javaToGroupReq(JNIEnv *env, jobject val, void* out) {
    struct tipc_group_req* gr = ((struct tipc_group_req *)out);
    gr->type = (*env)->CallIntMethod(env, val, kAFTIPCGroupRequestGetType);
    gr->instance = (*env)->CallIntMethod(env, val, kAFTIPCGroupRequestGetInstance);
    gr->scope = (*env)->CallIntMethod(env, val, kAFTIPCGroupRequestGetScopeId);
    gr->flags = (*env)->CallIntMethod(env, val, kAFTIPCGroupRequestGetFlagsValue);

    return JNI_TRUE;
}
#endif

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    getSocketOption
 * Signature: (Ljava/io/FileDescriptor;IILjava/lang/Class;)Ljava/lang/Object;
 */
JNIEXPORT jobject JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_getSocketOption
(JNIEnv *env, jclass clazz CK_UNUSED, jobject fd, jint domain, jint optionName, jclass valueType)
{
    if(valueType == NULL) {
        _throwException(env, kExceptionNullPointerException, "valueType");
        return NULL;
    }

    optionName = optionNameToNative(domain, optionName);
    domain = optionDomainToNative(domain);
    if(domain == -1 || optionName == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket option");
        return NULL;
    }

    int handle = _getFD(env, fd);

    socklen_t valLen;
    jobject (*valConverter)(JNIEnv *env, void* valPtr, socklen_t valLen);
    if(kIntegerClass != NULL && (*env)->IsAssignableFrom(env, valueType, kIntegerClass)) {
        valLen = sizeof(jint);
        valConverter = &intToInteger;
#if junixsocket_have_tipc
    } else if(kAFTIPCGroupRequestClass != NULL && (*env)->IsAssignableFrom(env, valueType, kAFTIPCGroupRequestClass)) {
        valLen = sizeof(struct tipc_group_req);
        valConverter = &groupReqToJava;
#endif
    } else {
        _throwException(env, kExceptionSocketException, "Unsupported value type");
        return NULL;
    }

    void *valPtr = calloc(valLen, 1);

    int ret = getsockopt(handle, domain, optionName,
                         WIN32_NEEDS_CHARP valPtr, &valLen);
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
        free(valPtr);
        return NULL;
    }

    jobject obj = valConverter(env, valPtr, valLen);
    if(obj == NULL) {
        _throwException(env, kExceptionSocketException, "Unsupported response");
    }
    free(valPtr);
    return obj;
}

/*
 * Class:     org_newsclub_net_unix_NativeUnixSocket
 * Method:    setSocketOption
 * Signature: (Ljava/io/FileDescriptor;IILjava/lang/Object;)V
 */
JNIEXPORT void JNICALL Java_org_newsclub_net_unix_NativeUnixSocket_setSocketOption
(JNIEnv *env, jclass clazz CK_UNUSED, jobject  fd, jint domain, jint optionName, jobject value)
{
    int handle = _getFD(env, fd);

    optionName = optionNameToNative(domain, optionName);
    domain = optionDomainToNative(domain);
    if(domain == -1 || optionName == -1) {
        _throwException(env, kExceptionSocketException, "Unsupported socket domain");
        return;
    }

    void* valPtr;
    socklen_t valLen;
    if(value == NULL) {
        valPtr = NULL;
        valLen = 0;
    } else {
        jclass objClass = (*env)->GetObjectClass(env, value);
        jboolean (*valConverter)(JNIEnv *,jobject,void*);

        if(kIntegerClass != NULL && (*env)->IsAssignableFrom(env, objClass, kIntegerClass)) {
            valLen = sizeof(jint);
            valConverter = &integerToInt;
#if junixsocket_have_tipc
        } else if(kAFTIPCGroupRequestClass != NULL && (*env)->IsAssignableFrom(env, objClass, kAFTIPCGroupRequestClass)) {
            valLen = sizeof(struct tipc_group_req);
            valConverter = &javaToGroupReq;
#endif
        } else {
            _throwException(env, kExceptionSocketException, "Unsupported value type");
            return;
        }
        valPtr = calloc(valLen, 1);

        if(!valConverter(env, value, valPtr)) {
            _throwException(env, kExceptionSocketException, "Unsupported value");
            goto end;
        }
    }

    int ret = setsockopt(handle, domain, optionName,
                         WIN32_NEEDS_CHARP valPtr, valLen);
    if(ret == -1) {
        _throwSockoptErrnumException(env, socket_errno, fd);
    }

end:
    free(valPtr);
}
