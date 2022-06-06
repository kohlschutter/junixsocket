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

#ifndef jniutil_h
#define jniutil_h

#include "config.h"

CK_VISIBILITY_INTERNAL void handleFieldNotFound(JNIEnv *env, jobject instance, char *fieldName);

CK_VISIBILITY_INTERNAL void callObjectSetter(JNIEnv *env, jobject instance, char *methodName,
                      char *methodSignature, jobject value);

CK_VISIBILITY_INTERNAL void setObjectFieldValue(JNIEnv *env, jobject instance, char *fieldName,
                         char *fieldType, jobject value);

CK_VISIBILITY_INTERNAL jboolean setObjectFieldValueIfPossible(JNIEnv *env, jobject instance, char *fieldName, char *fieldType, jobject value);

CK_VISIBILITY_INTERNAL void setLongFieldValue(JNIEnv *env, jobject instance, char *fieldName,
                       jlong value);

CK_VISIBILITY_INTERNAL jclass findClassAndGlobalRef(JNIEnv *env, char *className);
CK_VISIBILITY_INTERNAL jclass findClassAndGlobalRef0(JNIEnv *env, char *className, jboolean okIfMissing);
CK_VISIBILITY_INTERNAL void releaseClassGlobalRef(JNIEnv *env, jclass klazz);

struct jni_direct_byte_buffer_ref {
    void *buf; // pointer to buffer, or NULL
    union {
        ssize_t size; // size of buffer (or -1 on error)
        void *padding;
    };
};

/**
 * Ensures that a given direct byte buffer has a minimum size (which can be 0).
 *
 * If the requirement cannot be met (but a buffer was specified), it is guaranteed that both .buf==NULL and .capacity==-1.
 * If the requirement cannot be met because the byteBuffer specified was NULL, it is guaranteed that both
 * .BUF==NULL and .capacity==0.
 */
CK_VISIBILITY_INTERNAL struct jni_direct_byte_buffer_ref getDirectByteBufferRef(JNIEnv *env, jobject byteBuffer, size_t offset, size_t minSizeExpected);

#endif /* jniutil_h */
