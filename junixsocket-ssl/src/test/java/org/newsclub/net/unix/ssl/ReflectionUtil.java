/*
 * junixsocket
 *
 * Copyright 2009-2023 Christian Kohlschütter
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
package org.newsclub.net.unix.ssl;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Parameter;
import java.security.Provider;
import java.util.HashMap;
import java.util.Map;

final class ReflectionUtil {
  private static final Map<Class<?>, Class<?>> PRIMITIVE_TO_BOXED = new HashMap<>();

  static {
    PRIMITIVE_TO_BOXED.put(boolean.class, Boolean.class);
    PRIMITIVE_TO_BOXED.put(byte.class, Byte.class);
    PRIMITIVE_TO_BOXED.put(char.class, Character.class);
    PRIMITIVE_TO_BOXED.put(double.class, Double.class);
    PRIMITIVE_TO_BOXED.put(float.class, Float.class);
    PRIMITIVE_TO_BOXED.put(int.class, Integer.class);
    PRIMITIVE_TO_BOXED.put(long.class, Long.class);
    PRIMITIVE_TO_BOXED.put(short.class, Short.class);
  }

  private ReflectionUtil() {
    throw new IllegalStateException("No instances");
  }

  private static class ExceptionPlaceholder extends Exception {
    private static final long serialVersionUID = 1L;
  }

  @SuppressWarnings("unchecked")
  static Class<? extends Throwable> throwableByNameForAssertion(String className) {
    try {
      return (Class<? extends Throwable>) Class.forName(className);
    } catch (ClassNotFoundException e) {
      return ExceptionPlaceholder.class;
    }
  }

  @SuppressWarnings("PMD.CognitiveComplexity")
  static Provider instantiateIfPossible(String className, Object... args) {
    try {
      Class<?> klazz = Class.forName(className);
      Constructor<?> constructor = null;
      for (Constructor<?> constr : klazz.getConstructors()) {
        if (constr.getParameterCount() != args.length) {
          continue;
        }
        constructor = constr; // Assume we found it
        if (args.length == 0) {
          break;
        }

        Parameter[] params = constr.getParameters();
        int i = 0;

        for (Parameter param : params) {
          Class<?> paramClass = param.getType();
          Object arg = args[i++];

          if (arg == null) {
            if (paramClass.isPrimitive()) {
              constructor = null; // cannot be cast
              break;
            } else {
              // check other parameters
              continue;
            }
          }
          Class<?> argClass = arg.getClass();

          if (paramClass.isAssignableFrom(argClass)) {
            // check other parameters
            continue;
          } else if (paramClass.isPrimitive()) {
            Class<?> boxedClass = PRIMITIVE_TO_BOXED.get(paramClass);
            if (boxedClass != null) {
              if (boxedClass.isAssignableFrom(argClass)) {
                continue;
              } else {
                constructor = null; // cannot be cast
                break;
              }
            } else {
              constructor = null; // unexpected; cannot be cast
              break;
            }
          } else {
            constructor = null; // cannot be cast
            break;
          }
        }
        if (constructor != null) {
          break;
        }
      }
      if (constructor == null) {
        throw new NoSuchMethodException("Could not find constructor for " + className);
      }
      return (Provider) constructor.newInstance(args);
    } catch (InstantiationException | IllegalAccessException | IllegalArgumentException
        | InvocationTargetException | NoSuchMethodException | SecurityException
        | ClassNotFoundException | ClassCastException e) {
      // e.printStackTrace();
      return null; // NOPMD.ReturnEmptyCollectionRatherThanNull
    }
  }

}
