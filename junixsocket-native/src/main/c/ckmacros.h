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

#ifndef ckmacros_h
#define ckmacros_h

#if __clang__
#   define CK_IGNORE_UNUSED_MACROS_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-macros\"")
#   define CK_IGNORE_UNUSED_MACROS_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_CAST_ALIGN_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wcast-align\"")
#   define CK_IGNORE_CAST_ALIGN_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_SIGN_COMPARE_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wsign-compare\"")
#   define CK_IGNORE_SIGN_COMPARE_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_CAST_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wint-to-pointer-cast\"") \
_Pragma("clang diagnostic ignored \"-Wpointer-to-int-cast\"")
_Pragma("clang diagnostic ignored \"-Wbad-function-cast\"")
#   define CK_IGNORE_CAST_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wused-but-marked-unused\"")
#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_UNUSED_FUNCTION_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-function\"")
#   define CK_IGNORE_UNUSED_FUNCTION_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_UNUSED_VARIABLE_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunused-variable\"")
#   define CK_IGNORE_UNUSED_VARIABLE_END \
_Pragma("clang diagnostic pop")

#if __has_warning("-Wreserved-identifier")
#   define CK_IGNORE_RESERVED_IDENTIFIER_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wreserved-identifier\"")
#   define CK_IGNORE_RESERVED_IDENTIFIER_END \
_Pragma("clang diagnostic pop")
#else
#   define CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
#   define CK_IGNORE_RESERVED_IDENTIFIER_END
#endif

/**
 * Wrap code with #if CK_EXCLUDED_FROM_STATIC_ANALYSIS and #endif
 * to exclude said portion from static analysis
 */
#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS !(__clang_analyzer__)

#   define CK_STATIC_ASSERT(COND) _Static_assert(COND, "Assertion failed")

#define CK_FALLTHROUGH \
    _Pragma("clang diagnostic push") \
    _Pragma("clang diagnostic ignored \"-Wmissing-declarations\"") \
    __attribute__((fallthrough)) \
    _Pragma("clang diagnostic pop")

#else
#   define CK_IGNORE_UNUSED_MACROS_BEGIN
#   define CK_IGNORE_UNUSED_MACROS_END

#   define CK_IGNORE_CAST_ALIGN_BEGIN
#   define CK_IGNORE_CAST_ALIGN_END

#   define CK_IGNORE_SIGN_COMPARE_BEGIN
#   define CK_IGNORE_SIGN_COMPARE_END

#if __GNUC__
#   define CK_IGNORE_CAST_BEGIN \
_Pragma("GCC diagnostic push") \
_Pragma("GCC diagnostic ignored \"-Wint-to-pointer-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wpointer-to-int-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wbad-function-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wcast-function-type\"")
#   define CK_IGNORE_CAST_END \
_Pragma("GCC diagnostic pop")
#else
#   define CK_IGNORE_CAST_BEGIN
#   define CK_IGNORE_CAST_END
#endif

#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_BEGIN
#   define CK_IGNORE_USED_BUT_MARKED_UNUSED_END

#   define CK_IGNORE_UNUSED_FUNCTION_BEGIN
#   define CK_IGNORE_UNUSED_FUNCTION_END

#if __GNUC__
#   define CK_IGNORE_UNUSED_VARIABLE_BEGIN \
_Pragma("GCC diagnostic push") \
_Pragma("GCC diagnostic ignored \"-Wunused-variable\"")
#   define CK_IGNORE_UNUSED_VARIABLE_END \
_Pragma("GCC diagnostic pop")
#else
#   define CK_IGNORE_UNUSED_VARIABLE_BEGIN
#   define CK_IGNORE_UNUSED_VARIABLE_END
#endif

#   define CK_IGNORE_RESERVED_IDENTIFIER_BEGIN
#   define CK_IGNORE_RESERVED_IDENTIFIER_END

#   define CK_EXCLUDED_FROM_STATIC_ANALYSIS 1

#   define CK_STATIC_ASSERT(COND)

#   define CK_FALLTHROUGH __attribute__((fallthrough))

#endif

#define CK_UNUSED __attribute__((__unused__))

#if defined(_WIN32) || defined(__TOS_MVS__)
#  define CK_VISIBILITY_INTERNAL
#  define CK_VISIBILITY_DEFAULT
#elif __clang
#  define CK_VISIBILITY_INTERNAL __attribute__((visibility("internal")))
#  define CK_VISIBILITY_DEFAULT __attribute__((visibility("default")))
#else
#  define CK_VISIBILITY_INTERNAL __attribute__((visibility("hidden")))
#  define CK_VISIBILITY_DEFAULT __attribute__((visibility("default")))
#endif

/**
 * Marks arguments as "potentially unused" (depending on source configuration).
 */
#define CK_ARGUMENT_POTENTIALLY_UNUSED(X) (void)(X)

#define CK_UNREACHABLE_CODE do { if((true))abort(); } while(0);

#endif /* ckmacros_h */
