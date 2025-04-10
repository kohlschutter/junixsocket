/*
 * junixsocket
 *
 * Copyright 2009-2025 Christian Kohlschütter
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

#   define CK_IGNORE_BUFFER_ARITHMETIC_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunsafe-buffer-usage\"")
#   define CK_IGNORE_BUFFER_ARITHMETIC_END \
_Pragma("clang diagnostic pop")

#   define CK_IGNORE_PEDANTIC_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wpedantic\"")
#   define CK_IGNORE_PEDANTIC_END \
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

#   define CK_IGNORE_FORMAT_NONLITERAL_BEGIN \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wformat-nonliteral\"")
#   define CK_IGNORE_FORMAT_NONLITERAL_END \
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

#   define CK_STATIC_ASSERT(COND) \
_Pragma("clang diagnostic push") \
_Pragma("clang diagnostic ignored \"-Wunknown-warning-option\"") \
_Pragma("clang diagnostic ignored \"-Wpre-c11-compat\"") \
_Static_assert(COND, "Assertion failed") \
_Pragma("clang diagnostic pop")

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

#   define CK_IGNORE_BUFFER_ARITHMETIC_BEGIN
#   define CK_IGNORE_BUFFER_ARITHMETIC_END

#if __GNUC__
#   define CK_IGNORE_PEDANTIC_BEGIN \
_Pragma("GCC diagnostic push") \
_Pragma("GCC diagnostic ignored \"-Wpedantic\"")
#   define CK_IGNORE_PEDANTIC_END \
_Pragma("GCC diagnostic pop")

#   define CK_IGNORE_CAST_BEGIN \
_Pragma("GCC diagnostic push") \
_Pragma("GCC diagnostic ignored \"-Wint-to-pointer-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wpointer-to-int-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wbad-function-cast\"") \
_Pragma("GCC diagnostic ignored \"-Wcast-function-type\"")
#   define CK_IGNORE_CAST_END \
_Pragma("GCC diagnostic pop")
#else
#   define CK_IGNORE_PEDANTIC_BEGIN
#   define CK_IGNORE_PEDANTIC_END

#   define CK_IGNORE_CAST_BEGIN
#   define CK_IGNORE_CAST_END
#endif

#if __GNUC__
#   define CK_IGNORE_FORMAT_NONLITERAL_BEGIN \
_Pragma("GCC diagnostic push") \
_Pragma("GCC diagnostic ignored \"-Wformat-nonliteral\"")
#   define CK_IGNORE_FORMAT_NONLITERAL_END \
_Pragma("GCC diagnostic pop")
#else
#   define CK_IGNORE_FORMAT_NONLITERAL_BEGIN
#   define CK_IGNORE_FORMAT_NONLITERAL_END
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
#define CK_STRUCT_PACKED __attribute__((__packed__))
#define CK_ALIGNED_8 __attribute__((aligned(8)))

#if __TOS_MVS__
#  define CK_VISIBILITY_INTERNAL
#  define CK_VISIBILITY_DEFAULT
#endif
#ifdef _WIN32
#  define CK_VISIBILITY_INTERNAL
#  define CK_VISIBILITY_DEFAULT
#endif
#ifdef __TANDEM
#  define CK_VISIBILITY_INTERNAL
#  define CK_VISIBILITY_DEFAULT
#  undef CK_UNUSED
#  define CK_UNUSED
#  undef CK_STRUCT_PACKED
#  define CK_STRUCT_PACKED
#  undef CK_FALLTHROUGH
#  define CK_FALLTHROUGH
#  undef CK_ALIGNED_8
#  define CK_ALIGNED_8
#endif
#if __clang__
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

#ifdef __TOS_MVS__
#   define CK_INLINE_IF_POSSIBLE
#else
#   define CK_INLINE_IF_POSSIBLE inline
#endif

#endif /* ckmacros_h */
