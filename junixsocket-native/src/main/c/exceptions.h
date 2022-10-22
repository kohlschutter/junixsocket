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
    kExceptionNoRouteToHostException,
    kExceptionClosedChannelException,
    kExceptionInvalidArgumentSocketException,
    kExceptionAddressUnavailableSocketException,
    kExceptionOperationNotSupportedSocketException,
    kExceptionMaxExcl
} ExceptionType;

void init_exceptions(JNIEnv *env);
void destroy_exceptions(JNIEnv *env);

CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
void _throwException(JNIEnv* env, ExceptionType exceptionType, char* message);

void _throwErrnumException(JNIEnv* env, int errnum, jobject fdToClose);

void _throwSockoptErrnumException(JNIEnv* env, int errnum, jobject fd);
CK_IGNORE_RESERVED_IDENTIFIER_END

#endif /* exceptions_h */
