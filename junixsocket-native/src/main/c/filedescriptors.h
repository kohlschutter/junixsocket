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

#ifndef filedescriptors_h
#define filedescriptors_h

#include "config.h"

void init_filedescriptors(JNIEnv *env);
void destroy_filedescriptors(JNIEnv *env);

CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
CK_VISIBILITY_INTERNAL jint _getFD(JNIEnv *env, jobject fd);
void _initFD(JNIEnv *env, jobject fd, jint handle);

#if defined(_WIN32)
jlong _getHandle(JNIEnv * env, jobject fd);
void _initHandle(JNIEnv *env, jobject fd, jlong handle);
#endif

int _closeFd(JNIEnv *env, jobject fd, int handle);
CK_IGNORE_RESERVED_IDENTIFIER_END

jboolean checkNonBlocking(int handle, int errnum);
jboolean checkNonBlocking0(int handle, int errnum, jint options);

jboolean supportsCastAsRedirect(void);

#endif /* filedescriptors_h */
