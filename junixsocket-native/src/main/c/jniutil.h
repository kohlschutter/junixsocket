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

CK_VISIBILITY_INTERNAL void setObjectFieldValueIfPossible(JNIEnv *env, jobject instance, char *fieldName, char *fieldType, jobject value);

CK_VISIBILITY_INTERNAL void setLongFieldValue(JNIEnv *env, jobject instance, char *fieldName,
                       jlong value);

CK_VISIBILITY_INTERNAL jclass findClassAndGlobalRef(JNIEnv *env, char *className);
CK_VISIBILITY_INTERNAL void releaseClassGlobalRef(JNIEnv *env, jclass klazz);

#endif /* jniutil_h */
