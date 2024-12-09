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

#include "logging.h"
#include "jniutil.h"

#if DEBUG

static jclass kNativeLoggingClass;
static jmethodID kNativeLoggingLog = {0};

void init_logging(JNIEnv *env) {
    kNativeLoggingClass = findClassAndGlobalRef(env, "org/newsclub/net/unix/NativeLogging");
    if(kNativeLoggingClass != NULL) {
        kNativeLoggingLog = (*env)->GetStaticMethodID(env, kNativeLoggingClass, "log", "(Ljava/lang/String;)V");
    }

    (*env)->ExceptionClear(env);
}

void destroy_logging(JNIEnv *env) {
    releaseClassGlobalRef(env, kNativeLoggingClass);
}

void juxLog(JNIEnv *env, const char *format, ...) {
    if(kNativeLoggingLog) {
        va_list args;
        char buf[4096];

        va_start(args, format);
        CK_IGNORE_FORMAT_NONLITERAL_BEGIN
        vsnprintf(buf, 4096, format, args);
        CK_IGNORE_FORMAT_NONLITERAL_END
        va_end(args);

        jstring str = (*env)->NewStringUTF(env, buf);
        (*env)->CallStaticVoidMethod(env, kNativeLoggingClass, kNativeLoggingLog, str);
    }
}

#endif
