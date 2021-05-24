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

#ifndef exceptions_h
#define exceptions_h

#include "config.h"

typedef enum {
    kExceptionSocketException = 0,
    kExceptionSocketTimeoutException,
    kExceptionIndexOutOfBoundsException,
    kExceptionIllegalStateException,
    kExceptionNullPointerException,
    kExceptionMaxExcl
} ExceptionType;

CK_VISIBILITY_INTERNAL void _throwException(JNIEnv* env, ExceptionType exceptionType, char* message);

CK_VISIBILITY_INTERNAL void _throwErrnumException(JNIEnv* env, int errnum, jobject fdToClose);

CK_VISIBILITY_INTERNAL void _throwSockoptErrnumException(JNIEnv* env, int errnum, jobject fd);

#endif /* exceptions_h */