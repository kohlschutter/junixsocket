/**
 * junixsocket
 *
 * Copyright 2009-2020 Christian Kohlschütter
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
package org.newsclub.net.unix.demo;

import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.util.Objects;
import java.util.Properties;
import java.util.function.Function;

import org.newsclub.net.unix.AFUNIXSocket;
import org.newsclub.net.unix.AFUNIXSocketAddress;

/**
 * Just a helper class to simplify controlling the demo from the command line.
 */
public final class DemoHelper {

  private DemoHelper() {
    throw new IllegalStateException("No instances");
  }

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
  public static void addProperty(Properties props, String key, String defaultValue, String property,
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
        if (exampleValue.endsWith(")")) {
          example = "=" + exampleValue;
        } else {
          example = "=" + exampleValue + " (for example)";
        }
      }
      System.out.println(key + "=" + value + " -- override with -D" + property + example);
    }
    props.setProperty(key, value);
  }

  public static void initJDBCDriverClass(String property, String defaultValue, String exampleValue)
      throws ClassNotFoundException {

    if (exampleValue == null) {
      exampleValue = "(...)";
    } else {
      if (!exampleValue.endsWith(")")) {
        exampleValue += " (for example)";
      }
    }

    String driverClass = System.getProperty(property, defaultValue);
    if (driverClass.isEmpty()) {
      System.out.println("Using JDBC driver provided by SPI -- override with -D" + property + "="
          + exampleValue);
    } else {
      if (driverClass.equals(defaultValue)) {
        System.out.println("Using JDBC default driver " + driverClass + " -- override with -D"
            + property + "=" + exampleValue);
      } else {
        System.out.println("Using JDBC driver provided by -D" + property + "=" + driverClass);
      }
      Class.forName(driverClass);
    }
  }

  public static String getPropertyValue(String property, String defaultValue, String exampleValue) {
    return getPropertyValue(property, property, defaultValue, exampleValue, null);
  }

  public static <R> R getPropertyValue(String property, String defaultValue, String exampleValue,
      Function<String, R> valueConverter) {
    return getPropertyValue(property, property, defaultValue, exampleValue, valueConverter);
  }

  @SuppressWarnings("unchecked")
  public static <R> R getPropertyValue(String variable, String property, String defaultValue,
      String exampleValue, Function<String, R> valueConverter) {
    boolean print = true;
    if (exampleValue == null) {
      print = false;
    } else if (exampleValue.isEmpty()) {
      exampleValue = "(...)";
    } else {
      if (exampleValue.contains("$")) {
        exampleValue = "'" + exampleValue + "'";
      }
      if (!exampleValue.endsWith(")")) {
        exampleValue += " (for example)";
      }
    }

    String value = System.getProperty(property, defaultValue);

    if (print) {
      final String overrideOrSet;
      final String valueString;
      if (value == null) {
        valueString = "(not set)";
        overrideOrSet = "set";
      } else {
        valueString = value;
        overrideOrSet = "override";
      }

      if (Objects.equals(defaultValue, exampleValue)) {
        System.out.println(variable + "=" + valueString + " -- " + overrideOrSet + " with -D"
            + property + "=" + "(...)");
      } else {
        System.out.println(variable + "=" + valueString + " -- " + overrideOrSet + " with -D"
            + property + "=" + exampleValue);
      }
    }

    R returnValue;
    if (valueConverter != null) {
      returnValue = valueConverter.apply(value);
    } else {
      returnValue = (R) value;
    }

    return returnValue;
  }

  public static SocketAddress socketAddress(String socketName) throws IOException {
    int colon = socketName.lastIndexOf(':');
    int slashOrBackslash = Math.max(socketName.lastIndexOf('/'), socketName.lastIndexOf('\\'));

    if (socketName.startsWith("@")) {
      // abstract namespace (Linux only!)
      return AFUNIXSocketAddress.inAbstractNamespace(socketName.substring(1));
    } else if (colon > 0 && slashOrBackslash < colon && !socketName.startsWith("/")) {
      // assume TCP socket
      String hostname = socketName.substring(0, colon);
      int port = Integer.parseInt(socketName.substring(colon + 1));
      return new InetSocketAddress(hostname, port);
    } else {
      // assume unix socket file name
      return new AFUNIXSocketAddress(new File(socketName));
    }
  }

  public static Socket connectSocket(SocketAddress socketAddress) throws IOException {
    if (socketAddress instanceof AFUNIXSocketAddress) {
      return AFUNIXSocket.connectTo((AFUNIXSocketAddress) socketAddress);
    } else {
      Socket socket = new Socket();
      socket.connect(socketAddress);
      return socket;
    }
  }
}
