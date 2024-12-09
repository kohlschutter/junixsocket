//
//  futex.h
//  junixsocket-native
//
//  Created by Christian Kohlschütter on 01.12.24.
//

#ifndef futex_h
#define futex_h

#include "config.h"

void init_futex(JNIEnv *env);
void destroy_futex(JNIEnv *env);

#endif /* futex_h */
