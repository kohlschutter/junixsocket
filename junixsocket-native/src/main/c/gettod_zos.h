// -*- C++ -*-
//===----------------------------------------------------------------------===//
//
// Part of the LLVM Project, under the Apache License v2.0 with LLVM Exceptions.
// See https://llvm.org/LICENSE.txt for license information.
// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
//
//===----------------------------------------------------------------------===//

#ifndef _LIBCPP_SUPPORT_IBM_GETTOD_ZOS_H
#define _LIBCPP_SUPPORT_IBM_GETTOD_ZOS_H

#include <time.h>

static int gettimeofdayMonotonic(struct timespec* Output) {

  // The POSIX gettimeofday() function is not available on z/OS. Therefore,
  // we will call stcke and other hardware instructions in implement equivalent.
  // Note that nanoseconds alone will overflow when reaching new epoch in 2042.

  struct _t {
    uint64_t Hi;
    uint64_t Lo;
  };
  struct _t Value = {0, 0};
  uint64_t CC = 0;
  asm(" stcke %0\n"
      " ipm %1\n"
      " srlg %1,%1,28\n"
      : "=m"(Value), "+r"(CC)::);

  if (CC != 0) {
    errno = EMVSTODNOTSET;
    return CC;
  }
  uint64_t us = (Value.Hi >> 4);
  uint64_t ns = ((Value.Hi & 0x0F) << 8) + (Value.Lo >> 56);
  ns = (ns * 1000) >> 12;
  us = us - 2208988800000000;

  Output->tv_sec = us / 1000000;
  Output->tv_nsec = ns;

  return 0;
}

#endif // _LIBCPP_SUPPORT_IBM_GETTOD_ZOS_H
