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

#ifndef poll_h
#define poll_h

#include "config.h"

void init_poll(JNIEnv *env);
void destroy_poll(JNIEnv *env);

#if defined(junixsocket_use_poll_for_accept) || defined(junixsocket_use_poll_for_read)

jint pollWithTimeout(JNIEnv * env, jobject fd, int handle, int timeout);
jint pollWithMillis(int handle, uint64_t millis);

#endif

#endif /* poll_h */
