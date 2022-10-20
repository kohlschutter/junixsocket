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

#ifndef socket_h
#define socket_h

#include "config.h"

/**
 * Return some kind of "inode"-like identifier for the file at the given path, to be used
 * for identity checking.
 *
 * This could be the creation date, if no inode information is available.
 */
jlong getInodeIdentifier(char *filename);

int sockTypeToNative(JNIEnv *env, int type);

#endif /* socket_h */
