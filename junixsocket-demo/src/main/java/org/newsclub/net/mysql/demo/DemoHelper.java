/**
 * junixsocket
 *
 * Copyright 2009-2018 Christian Kohlschütter
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
package org.newsclub.net.mysql.demo;

import java.util.Properties;

/**
 * Just a helper class to simplify controlling the demo from the command line.
 */
class DemoHelper {
  /**
   * Adds a key-value pair to a Properties instance. Takes values from a given system property and
   * overrides the default value with it.
   * 
   * @param props The Properties instance to write to.
   * @param key The name of the property.
   * @param defaultValue The default value (for demo purposes)
   * @param property The name of the system property that can override the default value.
   * @param exampleValue An example value that is different from the default.
   */
  static void addProperty(Properties props, String key, String defaultValue, String property,
      String exampleValue) {
    String value = defaultValue;
    if (property == null) {
      System.out.println(key + "=" + value);
    } else {
      value = System.getProperty(property, value);
      String example;
      if (exampleValue == null) {
        example = "";
      } else {
        example = ", e.g.: -D" + property + "=" + exampleValue;
      }
      System.out.println(key + "=" + value + "; override with -D" + property + example);
    }
    props.setProperty(key, value);
  }
}
