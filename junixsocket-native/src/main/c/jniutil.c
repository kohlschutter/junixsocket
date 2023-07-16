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
#include "jniutil.h"

#include "exceptions.h"

void handleFieldNotFound(JNIEnv *env, jobject instance, char *fieldName)
{
    (*env)->ExceptionClear(env);

    jmethodID classMethodId = (*env)->GetMethodID(env,
                                                  (*env)->GetObjectClass(env, instance), "getClass",
                                                  "()Ljava/lang/Class;");
    jobject classObject = (*env)->CallObjectMethod(env, instance,
                                                   classMethodId);
    (*env)->ExceptionClear(env);

    jmethodID methodId = (*env)->GetMethodID(env,
                                             (*env)->GetObjectClass(env, classObject), "getName",
                                             "()Ljava/lang/String;");
    jstring className = (jstring)(*env)->CallObjectMethod(env, classObject,
                                                          methodId);
    if ((*env)->ExceptionCheck(env)) {
        return;
    }

    const char* classNameStr = (*env)->GetStringUTFChars(env, className, NULL);
    if(classNameStr == NULL) {
        return; // OOME
    }

#define handleFieldNotFound_error_message_template "Cannot find '%s' in class %s"

    size_t buflen = strlen(handleFieldNotFound_error_message_template) + strlen(fieldName) + strlen(classNameStr);
    char *message = calloc(1, buflen);
    CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN
    snprintf(message, buflen, handleFieldNotFound_error_message_template, fieldName, classNameStr);
    CK_IGNORE_USED_BUT_MARKED_UNUSED_END
    (*env)->ReleaseStringUTFChars(env, className, classNameStr);

    _throwException(env, kExceptionSocketException, message);
    free(message);
}

void callObjectSetter(JNIEnv *env, jobject instance, char *methodName,
                             char *methodSignature, jobject value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    if(instanceClass == NULL) {
        return;
    }

    jmethodID methodId = (*env)->GetMethodID(env, instanceClass, methodName,
                                             methodSignature);
    if(methodId == NULL) {
        handleFieldNotFound(env, instance, methodName);
        return;
    }

    __attribute__((aligned(8))) jobject array[] = {value};
    (*env)->CallVoidMethodA(env, instance, methodId, (jvalue*)array);
    if ((*env)->ExceptionCheck(env)) {
        return;
    }
}

void setObjectFieldValue(JNIEnv *env, jobject instance, char *fieldName,
                                char *fieldType, jobject value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    if(instanceClass == NULL) {
        return;
    }
    jfieldID fieldID = (*env)->GetFieldID(env, instanceClass, fieldName,
                                          fieldType);
    if(fieldID == NULL) {
        handleFieldNotFound(env, instance, fieldName);
        return;
    }
    (*env)->SetObjectField(env, instance, fieldID, value);
}

jboolean setObjectFieldValueIfPossible(JNIEnv *env, jobject instance, char *fieldName,
                                          char *fieldType, jobject value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    if(instanceClass == NULL) {
        return false;
    }
    jfieldID fieldID = (*env)->GetFieldID(env, instanceClass, fieldName,
                                          fieldType);
    if(fieldID == NULL) {
        // ignore
        (*env)->ExceptionClear(env);
        return false;
    }
    (*env)->SetObjectField(env, instance, fieldID, value);
    return true;
}

void setLongFieldValue(JNIEnv *env, jobject instance, char *fieldName,
                              jlong value)
{
    jclass instanceClass = (*env)->GetObjectClass(env, instance);
    jfieldID fieldID = (*env)->GetFieldID(env, instanceClass, fieldName, "J");
    if(fieldID == NULL) {
        handleFieldNotFound(env, instance, fieldName);
        return;
    }
    (*env)->SetLongField(env, instance, fieldID, value);
}

jclass findClassAndGlobalRef(JNIEnv *env, char *className) {
    return findClassAndGlobalRef0(env, className, JNI_FALSE);
}

jclass findClassAndGlobalRef0(JNIEnv *env, char *className, jboolean okIfMissing) {

    jclass clazz = (*env)->FindClass(env, className);
    if (clazz) {
        return (*env)->NewGlobalRef(env, clazz);
    } else if(okIfMissing) {
//#if DEBUG
//        fprintf(stderr, "(junixsocket) Could not find optional class %s\n", className);
//#endif
        (*env)->ExceptionClear(env);
        return NULL;
    } else {
#if DEBUG
        fprintf(stderr, "(junixsocket) Could not find class %s\n", className);
#endif
        return NULL;
    }
}
void releaseClassGlobalRef(JNIEnv *env, jclass klazz) {
    if (klazz) {
        (*env)->DeleteGlobalRef(env, klazz);
    }
}

struct jni_direct_byte_buffer_ref getDirectByteBufferRef(JNIEnv *env, jobject byteBuffer, size_t offset, size_t minSizeExpected) {
    jbyte *buf = (byteBuffer == NULL) ? NULL : (*env)->GetDirectBufferAddress(env, byteBuffer);
    jlong capacity = byteBuffer == NULL ? 0 : (socklen_t)((*env)->GetDirectBufferCapacity(env, byteBuffer) - offset);

    if(capacity < (jlong)minSizeExpected) {
        buf = NULL;
        capacity = byteBuffer == NULL ? 0 : -1;
    }

    struct jni_direct_byte_buffer_ref ref = {
        .buf = buf + offset,
        .size = (ssize_t)MIN(capacity, SSIZE_MAX)
    };

    return ref;
}
