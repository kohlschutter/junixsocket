/*
 * junixsocket
 *
 * Copyright 2009-2024 Christian Kohlschütter
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

#ifndef logging_h
#define logging_h

#include "config.h"
#if DEBUG

void init_logging(JNIEnv *env);

void destroy_logging(JNIEnv *env);

void juxLog(JNIEnv *env, const char *fmt, ...);

#else

#define juxLog(env, fmt, ...)

#endif // DEBUG

#endif /* logging_h */